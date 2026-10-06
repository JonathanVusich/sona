package org.sona.format.flac;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.exception.InvalidFormatException;
import org.sona.format.MD5Checksum;
import org.sona.format.ParsedAudio;
import org.sona.metadata.Tag;
import org.sona.metadata.TagSet;
import org.sona.metadata.TagValue;
import org.sona.samples.FlacSample;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
            final var result = new Flac().parseAllMetadata(inputStream);

            assertThat(result.metadataBlocks()).containsKeys(BlockType.STREAM_INFO, BlockType.VORBIS_COMMENT);
        }
    }

    @Test
    void parseColdplay() throws IOException, InvalidFormatException {
        final var parsed = parse(FlacSample.COLDPLAY);

        assertThat(parsed.duration().toSeconds()).isEqualTo(238);

        final var metadata = parsed.metadata();
        assertThat(metadata.search(Tag.TRACK_TITLE)).containsExactly(new TagValue.Str("A Whisper"));
        assertThat(metadata.search(Tag.TRACK_ARTIST)).containsExactly(new TagValue.Str("Coldplay"));
        assertThat(metadata.search(Tag.RELEASE_TITLE)).containsExactly(new TagValue.Str("A Rush of Blood to the Head"));
        assertThat(metadata.search(Tag.TRACK_NUMBER)).containsExactly(new TagValue.Int(9));
    }

    @Test
    void parsePicardTaggedFile() throws IOException, InvalidFormatException {
        final var parsed = parse(FlacSample.LUCAS_FLOYD);

        assertThat(parsed.duration()).isEqualTo(Duration.ofSeconds(57, 160_000_000));

        final var metadata = parsed.metadata();
        assertThat(metadata.search(Tag.TRACK_NUMBER)).containsExactly(new TagValue.Int(8));
        assertThat(metadata.search(Tag.DISC_NUMBER)).containsExactly(new TagValue.Int(2));
        assertThat(metadata.search(Tag.ALBUM_ARTIST)).containsExactly(new TagValue.Str("Philip Glass; Paul Barnes"));
        assertThat(metadata.search(Tag.RELEASE_YEAR)).containsExactly(new TagValue.Int(2016));
        assertThat(metadata.unknownTags())
                .contains(new TagSet("musicbrainz_trackid", Set.of(new TagValue.Str("d19c6734-0690-4ee6-9228-91b95f32554d"))));
    }

    @Test
    void parseFileWithOnlyUnknownTags() throws IOException, InvalidFormatException {
        final var parsed = parse(FlacSample.SAMPLE_3);

        assertThat(parsed.metadata().search(Tag.TRACK_TITLE)).isEmpty();
        assertThat(parsed.metadata().unknownTags())
                .containsExactly(new TagSet("encoder", Set.of(new TagValue.Str("Lavf58.29.100"))));
    }

    @Test
    void parseFileWithoutVorbisComment() throws IOException, InvalidFormatException {
        final var parsed = parse(flac(streamInfo()));

        assertThat(parsed.duration()).isEqualTo(Duration.ofSeconds(10));
        assertThat(parsed.metadata().unknownTags()).isEmpty();
    }

    @Test
    void fieldNamesAreCaseInsensitive() throws IOException, InvalidFormatException {
        final var parsed = parse(flac(streamInfo(), vorbisComment(new Field("TiTlE", "Mixed Case"))));

        assertThat(parsed.metadata().search(Tag.TRACK_TITLE)).containsExactly(new TagValue.Str("Mixed Case"));
    }

    @Test
    void rejectNonFlacInput() {
        final var notFlac = new ByteArrayInputStream("ID3\u0004 not a flac file".getBytes());

        assertThatThrownBy(() -> parse(notFlac)).isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void rejectMissingStreamInfo() {
        assertThatThrownBy(() -> parse(flac(vorbisComment(new Field("TITLE", "No Stream Info")))))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("STREAMINFO");
    }

    @Test
    void writeAllMetadataRoundTrips() throws IOException, InvalidFormatException {
        final var seekTable = new SeekTable(header(BlockType.SEEK_TABLE, 0), List.of(new SeekPoint(0, 0, (short) 4096)));
        final var padding = new Padding(header(BlockType.PADDING, 1024));
        final var written = metadata(streamInfo(), seekTable, vorbisComment(new Field("TITLE", "Round Trip")), padding);

        final var outputStream = new ByteArrayOutputStream();
        new Flac().writeAllMetadata(written, outputStream);
        final var read = new Flac().parseAllMetadata(new ByteArrayInputStream(outputStream.toByteArray()));

        assertThat(read).usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*header\\.lastBlock", ".*header\\.size")
                .isEqualTo(written);
        assertThat(read.metadataBlocks().get(BlockType.PADDING).getFirst().header())
                .isEqualTo(new BlockHeader(false, BlockType.PADDING, 1024));
    }

    private static StreamInfo streamInfo() {
        // 10 seconds of 16-bit stereo at 44.1 kHz.
        return new StreamInfo(header(BlockType.STREAM_INFO, 34), new MD5Checksum(new byte[16]),
                4096, 4096, 0, 0, 44_100, 2, 16, 441_000);
    }

    private static VorbisComment vorbisComment(final Field... fields) {
        return new VorbisComment(header(BlockType.VORBIS_COMMENT, 0), "sona-test", List.of(fields));
    }

    // Flac.writeAllMetadata works out the real size and last-block flag; only padding takes its size from here.
    private static BlockHeader header(final BlockType blockType, final int size) {
        return new BlockHeader(false, blockType, size);
    }

    private static FlacMetadata metadata(final Block... blocks) {
        return new FlacMetadata(Arrays.stream(blocks).collect(groupingBy(block -> block.header().blockType())));
    }

    private static InputStream flac(final Block... blocks) throws IOException {
        final var outputStream = new ByteArrayOutputStream();
        new Flac().writeAllMetadata(metadata(blocks), outputStream);
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    private static ParsedAudio parse(final FlacSample sample) throws IOException, InvalidFormatException {
        try (final var inputStream = sample.inputStream()) {
            return parse(inputStream);
        }
    }

    private static ParsedAudio parse(final InputStream inputStream) throws IOException, InvalidFormatException {
        return new Flac().parse(inputStream);
    }
}
