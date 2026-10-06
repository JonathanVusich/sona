package org.sona.format.flac;

import dev.javax.bitstream.BitOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Writes a FLAC stream. Only the metadata blocks are written for now; writing the audio frames that follow them comes
 * later.
 */
public final class FlacWriter {

    private final DataOutputStream outputStream;

    public FlacWriter(final OutputStream outputStream) {
        this.outputStream = new DataOutputStream(outputStream);
    }

    /**
     * Writes the "fLaC" marker and the metadata blocks, STREAMINFO first. Block sizes and the last-block flag are
     * worked out here rather than taken from each block's header, since edited blocks change size.
     */
    public void writeAllMetadata(final FlacMetadata flacMetadata) throws IOException {
        writeFlacHeader();

        final var blocks = Arrays.stream(BlockType.values())
                .flatMap(blockType -> flacMetadata.metadataBlocks().getOrDefault(blockType, List.of()).stream())
                .toList();
        for (int index = 0; index < blocks.size(); index++) {
            final var block = blocks.get(index);
            final var body = blockBody(block);
            final var lastBlock = index == blocks.size() - 1;
            writeBlockHeader(new BlockHeader(lastBlock, block.header().blockType(), body.length));
            outputStream.write(body);
        }
        outputStream.flush();
    }

    private byte[] blockBody(final Block block) throws IOException {
        final var body = new ByteArrayOutputStream();
        final var dataStream = new DataOutputStream(body);
        switch (block) {
            case StreamInfo streamInfo -> writeStreamInfo(dataStream, streamInfo);
            case Padding padding -> dataStream.write(new byte[padding.header().size()]);
            case SeekTable seekTable -> writeSeekTable(dataStream, seekTable);
            case VorbisComment vorbisComment -> writeVorbisComment(dataStream, vorbisComment);
            // Only the header of these blocks is kept when parsing, so their contents can't be written back yet.
            case Application _, CueSheet _, Picture _ -> throw new UnsupportedOperationException(
                    "Writing " + block.header().blockType() + " blocks is not supported yet");
        }
        return body.toByteArray();
    }

    private void writeStreamInfo(final DataOutputStream outputStream,
                                 final StreamInfo streamInfo) throws IOException {
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

    private void writeSeekTable(final DataOutputStream outputStream, final SeekTable seekTable) throws IOException {
        for (final var seekPoint : seekTable.seekPoints()) {
            outputStream.writeLong(seekPoint.sampleNumber());
            outputStream.writeLong(seekPoint.frameOffset());
            outputStream.writeShort(seekPoint.samplesInFrame());
        }
    }

    private void writeVorbisComment(final DataOutputStream outputStream,
                                    final VorbisComment vorbisComment) throws IOException {
        writeUtf8(outputStream, vorbisComment.vendor());

        // Little endian u32
        outputStream.writeInt(Integer.reverseBytes(vorbisComment.fields().size()));
        for (final var field : vorbisComment.fields()) {
            writeUtf8(outputStream, field.name() + "=" + field.content());
        }
    }

    private void writeUtf8(final DataOutputStream outputStream, final String value) throws IOException {
        final var bytes = value.getBytes(StandardCharsets.UTF_8);
        // Little endian u32 length
        outputStream.writeInt(Integer.reverseBytes(bytes.length));
        outputStream.write(bytes);
    }

    void writeFlacHeader() throws IOException {
        outputStream.write(Flac.FLAC_HEADER);
    }

    private void writeBlockHeader(final BlockHeader header) throws IOException {
        final var bitStream = BitOutputStream.wrap(outputStream, ByteOrder.BIG_ENDIAN);

        bitStream.writeBits(lastBlockFlag(header), 1);
        bitStream.writeBits(header.blockType().ordinal(), 7);
        bitStream.writeBits(header.size(), 24);
        bitStream.flush();
    }

    private static int lastBlockFlag(final BlockHeader header) {
        if (header.lastBlock()) {
            return 1;
        }
        return 0;
    }

}
