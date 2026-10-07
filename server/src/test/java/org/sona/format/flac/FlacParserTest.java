package org.sona.format.flac;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.exception.InvalidFormatException;
import org.sona.format.MD5Checksum;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagSet;
import org.sona.metadata.TagValue;
import org.sona.samples.FlacSample;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlacParserTest {

    @ParameterizedTest
    @EnumSource(FlacSample.class)
    void parseAllMetadataReadsEveryBlock(FlacSample sample) throws IOException, InvalidFormatException {
        try (final var inputStream = sample.inputStream()) {
            final var result = new FlacParser().parseAllMetadata(inputStream);

            assertThat(result.metadataBlocks()).containsKeys(BlockType.STREAM_INFO, BlockType.VORBIS_COMMENT);
        }
    }

    @Test
    void parseColdplay() throws IOException, InvalidFormatException {
        final var metadata = parse(FlacSample.COLDPLAY);

        assertThat(metadata.duration().toSeconds()).isEqualTo(238);

        assertThat(metadata.search(Tag.TRACK_TITLE)).containsExactly(new TagValue.Str("A Whisper"));
        assertThat(metadata.search(Tag.TRACK_ARTIST)).containsExactly(new TagValue.Str("Coldplay"));
        assertThat(metadata.search(Tag.RELEASE_TITLE)).containsExactly(new TagValue.Str("A Rush of Blood to the Head"));
        assertThat(metadata.search(Tag.TRACK_NUMBER)).containsExactly(new TagValue.Int(9));
    }

    @Test
    void parsePicardTaggedFile() throws IOException, InvalidFormatException {
        final var metadata = parse(FlacSample.LUCAS_FLOYD);

        assertThat(metadata.duration()).isEqualTo(Duration.ofSeconds(57, 160_000_000));

        assertThat(metadata.search(Tag.TRACK_NUMBER)).containsExactly(new TagValue.Int(8));
        assertThat(metadata.search(Tag.DISC_NUMBER)).containsExactly(new TagValue.Int(2));
        assertThat(metadata.search(Tag.ALBUM_ARTIST)).containsExactly(new TagValue.Str("Philip Glass; Paul Barnes"));
        assertThat(metadata.search(Tag.RELEASE_YEAR)).containsExactly(new TagValue.Int(2016));
        assertThat(metadata.unknownTags())
                .contains(new TagSet("musicbrainz_trackid", Set.of(new TagValue.Str("d19c6734-0690-4ee6-9228-91b95f32554d"))));
    }

    @Test
    void parseEmbeddedPicture() throws IOException, InvalidFormatException {
        try (final var inputStream = FlacSample.LUCAS_FLOYD.inputStream()) {
            final var metadata = new FlacParser().parseAllMetadata(inputStream);

            final var picture = (Picture) metadata.metadataBlocks().get(BlockType.PICTURE).getFirst();
            // Picture type 3 is the front cover.
            assertThat(picture.pictureType()).isEqualTo(3);
            assertThat(picture.mediaType()).isEqualTo("image/jpeg");
            assertThat(picture.description()).isEmpty();
            assertThat(picture.width()).isEqualTo(360);
            assertThat(picture.height()).isEqualTo(327);
            assertThat(picture.data()).hasSize(62_895).startsWith(0xFF, 0xD8);
        }
    }

    @Test
    void parseFileWithOnlyUnknownTags() throws IOException, InvalidFormatException {
        final var parsed = parse(FlacSample.SAMPLE_3);

        assertThat(parsed.search(Tag.TRACK_TITLE)).isEmpty();
        assertThat(parsed.unknownTags())
                .containsExactly(new TagSet("encoder", Set.of(new TagValue.Str("Lavf58.29.100"))));
    }

    @Test
    void parseFileWithoutVorbisComment() throws IOException, InvalidFormatException {
        final var parsed = parse(flac(streamInfo(lastHeader(BlockType.STREAM_INFO))));

        assertThat(parsed.duration()).isEqualTo(Duration.ofSeconds(10));
        assertThat(parsed.unknownTags()).isEmpty();
    }

    @Test
    void fieldNamesAreCaseInsensitive() throws IOException, InvalidFormatException {
        final var parsed = parse(flac(streamInfo(header(BlockType.STREAM_INFO)),
                vorbisComment(lastHeader(BlockType.VORBIS_COMMENT), new Field("TiTlE", "Mixed Case"))));

        assertThat(parsed.search(Tag.TRACK_TITLE)).containsExactly(new TagValue.Str("Mixed Case"));
    }

    @Test
    void rejectNonFlacInput() {
        final var notFlac = new ByteArrayInputStream("ID3\u0004 not a flac file".getBytes());

        assertThatThrownBy(() -> parse(notFlac)).isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void rejectMissingStreamInfo() {
        assertThatThrownBy(() -> parse(flac(vorbisComment(lastHeader(BlockType.VORBIS_COMMENT), new Field("TITLE", "No Stream Info")))))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("STREAMINFO");
    }

    @Test
    void writtenBlocksParseBackUnchanged() throws IOException, InvalidFormatException {
        final var blocks = new Block[]{
                streamInfo(header(BlockType.STREAM_INFO)),
                new SeekTable(header(BlockType.SEEK_TABLE), List.of(new SeekPoint(0, 0, (short) 4096))),
                vorbisComment(header(BlockType.VORBIS_COMMENT), new Field("TITLE", "Round Trip")),
                new Application(header(BlockType.APPLICATION), 0x52494646, new byte[]{1, 2, 3, 4}),
                cueSheet(header(BlockType.CUESHEET)),
                new Picture(header(BlockType.PICTURE), 3, "image/png", "Front ✓", 2, 1, 24, 0, new byte[]{9, 8, 7}),
                new Padding(new BlockHeader(true, BlockType.PADDING, 1024))
        };

        final var read = new FlacParser().parseAllMetadata(flac(blocks));

        // The writer works out each block's size, so only padding's size is known up front.
        assertThat(read).usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*header\\.size")
                .isEqualTo(new FlacMetadata(Arrays.stream(blocks).collect(groupingBy(block -> block.header().blockType()))));
        assertThat(read.metadataBlocks().get(BlockType.PADDING).getFirst().header().size()).isEqualTo(1024);
        // Sizes from the RFC 9639 layouts: a cuesheet is 396 bytes plus 36 per track and 12 per index point, and a
        // picture is 32 bytes plus its media type, description and data.
        assertThat(read.metadataBlocks().get(BlockType.CUESHEET).getFirst().header().size()).isEqualTo(396 + 3 * 36 + 3 * 12);
        assertThat(read.metadataBlocks().get(BlockType.PICTURE).getFirst().header().size()).isEqualTo(32 + 9 + 9 + 3);
    }

    private static CueSheet cueSheet(final BlockHeader header) {
        final var firstTrack = new CueSheetTrack(0, 1, "USSM19900000", true, false,
                List.of(new CueSheetIndexPoint(0, 1)));
        final var dataTrack = new CueSheetTrack(588 * 100, 2, "", false, true,
                List.of(new CueSheetIndexPoint(0, 0), new CueSheetIndexPoint(588 * 2, 1)));
        // CD-DA cuesheets end with a lead-out track (number 170) that has no index points.
        final var leadOut = new CueSheetTrack(588 * 300, 170, "", true, false, List.of());
        return new CueSheet(header, "1234567890123", 88_200, true, List.of(firstTrack, dataTrack, leadOut));
    }

    private static StreamInfo streamInfo(final BlockHeader header) {
        // 10 seconds of 16-bit stereo at 44.1 kHz.
        return new StreamInfo(header, new MD5Checksum(new byte[16]), 4096, 4096, 0, 0, 44_100, 2, 16, 441_000);
    }

    private static VorbisComment vorbisComment(final BlockHeader header, final Field... fields) {
        return new VorbisComment(header, "sona-test", List.of(fields));
    }

    // FlacWriter works out the size of every block but padding, so it's left at 0 here.
    private static BlockHeader header(final BlockType blockType) {
        return new BlockHeader(false, blockType, 0);
    }

    private static BlockHeader lastHeader(final BlockType blockType) {
        return new BlockHeader(true, blockType, 0);
    }

    private static InputStream flac(final Block... blocks) throws IOException {
        final var outputStream = new ByteArrayOutputStream();
        final var dataStream = new DataOutputStream(outputStream);
        final var flacWriter = new FlacWriter();
        flacWriter.writeFlacHeader(dataStream);
        for (final var block : blocks) {
            flacWriter.writeBlock(dataStream, block);
        }
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    private static RawMetadata parse(final FlacSample sample) throws IOException, InvalidFormatException {
        try (final var inputStream = sample.inputStream()) {
            return parse(inputStream);
        }
    }

    private static RawMetadata parse(final InputStream inputStream) throws IOException, InvalidFormatException {
        return new FlacParser().parse(inputStream);
    }
}
