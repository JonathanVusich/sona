package org.sona.format.flac;

import dev.javax.bitstream.BitOutputStream;
import org.sona.format.Format;
import org.sona.format.Writer;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.TagValue;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import static java.util.function.Predicate.not;

public final class FlacWriter implements Writer {

    private static final int STREAM_INFO_SIZE = 34;
    private static final int SEEK_POINT_SIZE = 18;
    // Catalog number, lead-in samples, CD-DA flag with its reserved bits, and the track count.
    private static final int CUE_SHEET_SIZE = FlacParser.MEDIA_CATALOG_NUMBER_SIZE + 8 + 259 + 1;
    // Offset, number, ISRC, flags with their reserved bits, and the index point count.
    private static final int CUE_SHEET_TRACK_SIZE = 8 + 1 + FlacParser.ISRC_SIZE + 14 + 1;
    private static final int CUE_SHEET_INDEX_POINT_SIZE = 12;
    // The picture type, the two string lengths, width, height, color depth, color count and data length.
    private static final int PICTURE_FIELDS_SIZE = 8 * 4;

    @Override
    public Format format() {
        return Format.FLAC;
    }

    @Override
    public void write(final RawMetadata rawMetadata, final OutputStream outputStream) throws IOException {
        final var dataStream = new DataOutputStream(outputStream);

        // Write FLAC header
        writeFlacHeader(dataStream);

        final var flacTags = Arrays.stream(FlacTag.values())
                .map(FlacTag::getTag)
                .map(tag -> retrieve(rawMetadata, tag))
                .filter(not(Objects::isNull))
                .toList();

        final var rawTags = rawMetadata.unknownTags()
                .toList();
    }

    private TagValue retrieve(RawMetadata metadata, org.sona.metadata.Tag tag) {
        return metadata.search(tag).stream().findFirst().orElse(null);
    }

    void writeFlacHeader(final DataOutputStream outputStream) throws IOException {
        outputStream.write(FlacParser.FLAC_HEADER);
    }

    /**
     * Writes one metadata block straight to the stream. Its size is worked out from its contents, since edited blocks
     * change size; the last-block flag is taken from its header, because only the caller knows whether more blocks
     * follow.
     */
    void writeBlock(final DataOutputStream outputStream, final Block block) throws IOException {
        switch (block) {
            case StreamInfo streamInfo -> writeStreamInfo(outputStream, streamInfo);
            case Padding padding -> writePadding(outputStream, padding);
            case SeekTable seekTable -> writeSeekTable(outputStream, seekTable);
            case VorbisComment vorbisComment -> writeVorbisComment(outputStream, vorbisComment);
            case Application application -> writeApplication(outputStream, application);
            case CueSheet cueSheet -> writeCueSheet(outputStream, cueSheet);
            case Picture picture -> writePicture(outputStream, picture);
        }
    }

    private void writeStreamInfo(final DataOutputStream outputStream,
                                 final StreamInfo streamInfo) throws IOException {
        writeBlockHeader(outputStream, streamInfo.header(), STREAM_INFO_SIZE);

        final var bitOutputStream = BitOutputStream.wrap(outputStream, ByteOrder.BIG_ENDIAN);

        bitOutputStream.writeBits(streamInfo.minBlockSize(), 16);
        bitOutputStream.writeBits(streamInfo.maxBlockSize(), 16);
        bitOutputStream.writeBits(streamInfo.minFrameSize(), 24);
        bitOutputStream.writeBits(streamInfo.maxFrameSize(), 24);

        bitOutputStream.writeBits(streamInfo.sampleRate(), 20);
        bitOutputStream.writeBits(streamInfo.numberOfChannels() - 1, 3);
        bitOutputStream.writeBits(streamInfo.bitsPerSample() - 1, 5);
        bitOutputStream.writeBits(streamInfo.interChannelSamples(), 36);
        bitOutputStream.flush();

        outputStream.write(streamInfo.checksum().getChecksum());
    }

    private void writePadding(final DataOutputStream outputStream, final Padding padding) throws IOException {
        final var size = padding.header().size();
        writeBlockHeader(outputStream, padding.header(), size);
        outputStream.write(new byte[size]);
    }

    private void writeSeekTable(final DataOutputStream outputStream, final SeekTable seekTable) throws IOException {
        final var seekPoints = seekTable.seekPoints();
        writeBlockHeader(outputStream, seekTable.header(), seekPoints.size() * SEEK_POINT_SIZE);

        for (final var seekPoint : seekPoints) {
            outputStream.writeLong(seekPoint.sampleNumber());
            outputStream.writeLong(seekPoint.frameOffset());
            outputStream.writeShort(seekPoint.samplesInFrame());
        }
    }

    private void writeVorbisComment(final DataOutputStream outputStream,
                                    final VorbisComment vorbisComment) throws IOException {
        final var vendor = vorbisComment.vendor().getBytes(StandardCharsets.UTF_8);
        final var fields = vorbisComment.fields().stream()
                .map(field -> (field.name() + "=" + field.content()).getBytes(StandardCharsets.UTF_8))
                .toList();
        // The vendor and every field are prefixed with their u32 length, and the fields with their u32 count.
        final var fieldsSize = fields.stream().mapToInt(field -> 4 + field.length).sum();
        writeBlockHeader(outputStream, vorbisComment.header(), 4 + vendor.length + 4 + fieldsSize);

        writeUtf8(outputStream, vendor);
        // Little endian u32
        outputStream.writeInt(Integer.reverseBytes(fields.size()));
        for (final var field : fields) {
            writeUtf8(outputStream, field);
        }
    }

    private void writeApplication(final DataOutputStream outputStream,
                                  final Application application) throws IOException {
        final var data = application.data();
        writeBlockHeader(outputStream, application.header(), 4 + data.length);

        outputStream.writeInt(application.applicationId());
        outputStream.write(data);
    }

    private void writeCueSheet(final DataOutputStream outputStream, final CueSheet cueSheet) throws IOException {
        final var tracks = cueSheet.tracks();
        final var tracksSize = tracks.stream()
                .mapToInt(track -> CUE_SHEET_TRACK_SIZE + track.indexPoints().size() * CUE_SHEET_INDEX_POINT_SIZE)
                .sum();
        writeBlockHeader(outputStream, cueSheet.header(), CUE_SHEET_SIZE + tracksSize);

        writeAscii(outputStream, cueSheet.mediaCatalogNumber(), FlacParser.MEDIA_CATALOG_NUMBER_SIZE);
        outputStream.writeLong(cueSheet.leadInSamples());

        final var bitOutputStream = BitOutputStream.wrap(outputStream, ByteOrder.BIG_ENDIAN);
        bitOutputStream.writeBits(compactDiscFlag(cueSheet), 1);
        // Reserved: the rest of this byte plus 258 more
        bitOutputStream.writeBits(0, 7);
        bitOutputStream.flush();
        outputStream.write(new byte[258]);

        outputStream.writeByte(tracks.size());
        for (final var track : tracks) {
            writeCueSheetTrack(outputStream, track);
        }
    }

    private void writeCueSheetTrack(final DataOutputStream outputStream,
                                    final CueSheetTrack track) throws IOException {
        outputStream.writeLong(track.offset());
        outputStream.writeByte(track.number());
        writeAscii(outputStream, track.isrc(), FlacParser.ISRC_SIZE);

        final var bitOutputStream = BitOutputStream.wrap(outputStream, ByteOrder.BIG_ENDIAN);
        bitOutputStream.writeBits(trackTypeFlag(track), 1);
        bitOutputStream.writeBits(preEmphasisFlag(track), 1);
        // Reserved: the rest of this byte plus 13 more
        bitOutputStream.writeBits(0, 6);
        bitOutputStream.flush();
        outputStream.write(new byte[13]);

        final var indexPoints = track.indexPoints();
        outputStream.writeByte(indexPoints.size());
        for (final var indexPoint : indexPoints) {
            outputStream.writeLong(indexPoint.offset());
            outputStream.writeByte(indexPoint.number());
            // Reserved
            outputStream.write(new byte[3]);
        }
    }

    private void writePicture(final DataOutputStream outputStream, final Picture picture) throws IOException {
        final var mediaType = picture.mediaType().getBytes(StandardCharsets.US_ASCII);
        final var description = picture.description().getBytes(StandardCharsets.UTF_8);
        final var data = picture.data();
        final var size = PICTURE_FIELDS_SIZE + mediaType.length + description.length + data.length;
        writeBlockHeader(outputStream, picture.header(), size);

        // Unlike Vorbis comment lengths, picture lengths are big endian.
        outputStream.writeInt(picture.pictureType());
        outputStream.writeInt(mediaType.length);
        outputStream.write(mediaType);
        outputStream.writeInt(description.length);
        outputStream.write(description);

        outputStream.writeInt(picture.width());
        outputStream.writeInt(picture.height());
        outputStream.writeInt(picture.colorDepth());
        outputStream.writeInt(picture.numberOfColors());

        outputStream.writeInt(data.length);
        outputStream.write(data);
    }

    // Fixed-size ASCII fields are padded with NUL bytes.
    private void writeAscii(final DataOutputStream outputStream, final String value, final int size) throws IOException {
        final var bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > size) {
            throw new IllegalArgumentException("\"" + value + "\" is longer than " + size + " bytes");
        }
        outputStream.write(bytes);
        outputStream.write(new byte[size - bytes.length]);
    }

    private void writeUtf8(final DataOutputStream outputStream, final byte[] utf8) throws IOException {
        // Little endian u32 length
        outputStream.writeInt(Integer.reverseBytes(utf8.length));
        outputStream.write(utf8);
    }

    private void writeBlockHeader(final DataOutputStream outputStream,
                                  final BlockHeader header,
                                  final int size) throws IOException {
        final var bitStream = BitOutputStream.wrap(outputStream, ByteOrder.BIG_ENDIAN);

        bitStream.writeBits(lastBlockFlag(header), 1);
        bitStream.writeBits(header.blockType().ordinal(), 7);
        bitStream.writeBits(size, 24);
        bitStream.flush();
    }

    private static int lastBlockFlag(final BlockHeader header) {
        if (header.lastBlock()) {
            return 1;
        }
        return 0;
    }

    private static int compactDiscFlag(final CueSheet cueSheet) {
        if (cueSheet.compactDisc()) {
            return 1;
        }
        return 0;
    }

    // The track type bit is 0 for audio and 1 for anything else.
    private static int trackTypeFlag(final CueSheetTrack track) {
        if (track.audio()) {
            return 0;
        }
        return 1;
    }

    private static int preEmphasisFlag(final CueSheetTrack track) {
        if (track.preEmphasis()) {
            return 1;
        }
        return 0;
    }

}
