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
            // Only the header of these blocks is kept when parsing, so their contents can't be written back yet.
            case Application _, CueSheet _, Picture _ -> throw new UnsupportedOperationException(
                    "Writing " + block.header().blockType() + " blocks is not supported yet");
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

}
