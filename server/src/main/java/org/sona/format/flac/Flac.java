package org.sona.format.flac;

import dev.javax.bitstream.BitInputStream;
import dev.javax.bitstream.BitOutputStream;
import org.sona.exception.InvalidFormatException;
import org.sona.format.Format;
import org.sona.format.MD5Checksum;
import org.sona.format.ParsedAudio;
import org.sona.format.Parser;
import org.sona.format.Writer;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.TagValue;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static java.util.function.Predicate.not;

public final class Flac implements Parser, Writer {

    private static final byte[] FLAC_HEADER = "fLaC".getBytes(StandardCharsets.US_ASCII);

    private static final int OUTPUT_SIZE = 8192;

    @Override
    public Format format() {
        return Format.FLAC;
    }

    @Override
    public ParsedAudio parse(final InputStream inputStream) throws IOException, InvalidFormatException {
        final var flacMetadata = parseAllMetadata(inputStream);
        final var streamInfo = flacMetadata.metadataBlocks().getOrDefault(BlockType.STREAM_INFO, List.of()).stream()
                .map(StreamInfo.class::cast)
                .findFirst()
                .orElseThrow(() -> new InvalidFormatException("FLAC file is missing its STREAMINFO block!"));
        final var commentBlocks = flacMetadata.metadataBlocks().getOrDefault(BlockType.VORBIS_COMMENT, List.of());
        final var comments = commentBlocks.stream()
                .filter(VorbisComment.class::isInstance)
                .map(VorbisComment.class::cast)
                .toList();

        final var rawMetadata = new RawMetadata();

        for (final var comment : comments) {
            for (final var field : comment.fields()) {

                final var fieldName = field.name().toLowerCase();
                final var flacTag = FlacTag.from(fieldName);
                if (flacTag != null) {
                    rawMetadata.add(flacTag.getTag(), flacTag.convert(field.content()));
                } else {
                    rawMetadata.add(fieldName, field.content());
                }
            }
        }

        return new ParsedAudio(duration(streamInfo), rawMetadata);
    }

    @Override
    public OutputStream write(final RawMetadata rawMetadata) throws IOException {
        final var dataStream = new DataOutputStream(new ByteArrayOutputStream(OUTPUT_SIZE));

        // Write FLAC header
        writeFlacHeader(dataStream);

        final var flacTags = Arrays.stream(FlacTag.values())
                .map(FlacTag::getTag)
                .map(tag -> retrieve(rawMetadata, tag))
                .filter(not(Objects::isNull))
                .toList();

        final var rawTags = rawMetadata.unknownTags()
                .toList();

        return null;
    }

    private TagValue retrieve(RawMetadata metadata, org.sona.metadata.Tag tag) {
        return metadata.search(tag).stream().findFirst().orElse(null);
    }

    private static Duration duration(final StreamInfo streamInfo) {
        final var samples = streamInfo.interChannelSamples();
        final var sampleRate = streamInfo.sampleRate();
        return Duration.ofSeconds(samples / sampleRate, (samples % sampleRate) * 1_000_000_000L / sampleRate);
    }

    FlacMetadata parseAllMetadata(final InputStream inputStream) throws IOException, InvalidFormatException {
        final var dataStream = new DataInputStream(inputStream);
        // Validate FLAC header
        validateFlacHeader(dataStream);

        final Map<BlockType, List<Block>> metadataBlocks = new EnumMap<>(BlockType.class);
        // available() can't tell whether more blocks follow, so read until the block marked last.
        Block block;
        do {
            block = readBlock(dataStream);
            metadataBlocks.computeIfAbsent(block.header().blockType(), k -> new ArrayList<>()).add(block);
        } while (!block.header().lastBlock());

        return new FlacMetadata(metadataBlocks);
    }

    /**
     * Writes the "fLaC" marker and the metadata blocks, STREAMINFO first. Block sizes and the last-block flag are
     * worked out here rather than taken from each block's header, since edited blocks change size.
     */
    public void writeAllMetadata(final FlacMetadata flacMetadata,
                                 final OutputStream outputStream) throws IOException {
        final var dataStream = new DataOutputStream(outputStream);
        writeFlacHeader(dataStream);

        final var blocks = Arrays.stream(BlockType.values())
                .flatMap(blockType -> flacMetadata.metadataBlocks().getOrDefault(blockType, List.of()).stream())
                .toList();
        for (int index = 0; index < blocks.size(); index++) {
            final var block = blocks.get(index);
            final var body = blockBody(block);
            final var lastBlock = index == blocks.size() - 1;
            writeBlockHeader(dataStream, new BlockHeader(lastBlock, block.header().blockType(), body.length));
            dataStream.write(body);
        }
        dataStream.flush();
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

    private StreamInfo readStreamInfo(final DataInputStream inputStream,
                                      final BlockHeader header) throws IOException {
        final var bitInputStream = BitInputStream.wrap(inputStream, ByteOrder.BIG_ENDIAN);

        final int minBlockSize = (int) bitInputStream.readBits(16);
        final int maxBlockSize = (int) bitInputStream.readBits(16);
        final int minFrameSize = (int) bitInputStream.readBits(24);
        final int maxFrameSize = (int) bitInputStream.readBits(24);

        final int sampleRate = (int) bitInputStream.readBits(20);
        final int numberOfChannels = (int) bitInputStream.readBits(3) + 1;
        final int bitsPerSample = (int) bitInputStream.readBits(5) + 1;
        final long interChannelSamples = bitInputStream.readBits(36);

        final byte[] md5Checksum = inputStream.readNBytes(16);

        return new StreamInfo(
                header,
                new MD5Checksum(md5Checksum),
                minBlockSize,
                maxBlockSize,
                minFrameSize,
                maxFrameSize,
                sampleRate,
                numberOfChannels,
                bitsPerSample,
                interChannelSamples
        );
    }

    private Padding readPadding(final DataInputStream inputStream, final BlockHeader header) throws IOException {
        inputStream.skipNBytes(header.size());
        return new Padding(header);
    }

    private Application readApplication(final DataInputStream inputStream, final BlockHeader header) throws IOException {
        final var applicationId = inputStream.readInt();
        inputStream.skipNBytes(header.size() - 4);
        return new Application(header, applicationId);
    }

    private SeekTable readSeekTable(final DataInputStream inputStream, final BlockHeader header) throws IOException {
        final var numberOfSeekPoints = header.size() / 18;

        final var seekPoints = new ArrayList<SeekPoint>(numberOfSeekPoints);
        for (int i = 0; i < numberOfSeekPoints; i++) {
            final var sampleNumber = inputStream.readLong();
            final var frameOffset = inputStream.readLong();
            final var samplesInFrame = inputStream.readShort();

            final var seekPoint = new SeekPoint(sampleNumber, frameOffset, samplesInFrame);
            seekPoints.add(seekPoint);
        }

        return new SeekTable(header, seekPoints);
    }

    private VorbisComment readVorbisComment(final DataInputStream inputStream, final BlockHeader header) throws IOException {
        // Little endian u32
        final var length = Integer.reverseBytes(inputStream.readInt());
        final var vendor = readUtf8(inputStream, length);

        final var numberOfFields = Integer.reverseBytes(inputStream.readInt());

        final var fields = new ArrayList<Field>(numberOfFields);
        for (int i = 0; i < numberOfFields; i++) {
            // Little endian
            final var fieldLength = Integer.reverseBytes(inputStream.readInt());
            final var fieldContent = readUtf8(inputStream, fieldLength);

            final var fieldEndIdx = fieldContent.indexOf("=");
            final var name = fieldContent.substring(0, fieldEndIdx);
            final var content = fieldContent.substring(fieldEndIdx + 1);

            fields.add(new Field(name, content));
        }

        return new VorbisComment(header, vendor, fields);
    }

    private CueSheet readCueSheet(final DataInputStream inputStream, final BlockHeader header) throws IOException {
        inputStream.skipNBytes(header.size());
        return new CueSheet(header);
    }

    private Picture readPicture(final DataInputStream inputStream, final BlockHeader header) throws IOException {
        inputStream.skipNBytes(header.size());
        return new Picture(header);
    }

    private Block readBlock(final DataInputStream inputStream) throws IOException, InvalidFormatException {
        final var header = readBlockHeader(inputStream);

        return switch (header.blockType()) {
            case STREAM_INFO -> readStreamInfo(inputStream, header);
            case PADDING -> readPadding(inputStream, header);
            case APPLICATION -> readApplication(inputStream, header);
            case SEEK_TABLE -> readSeekTable(inputStream, header);
            case VORBIS_COMMENT -> readVorbisComment(inputStream, header);
            case CUESHEET -> readCueSheet(inputStream, header);
            case PICTURE -> readPicture(inputStream, header);
        };
    }

    private String readUtf8(final DataInputStream inputStream, final int length) throws IOException {
        return new String(inputStream.readNBytes(length), StandardCharsets.UTF_8);
    }


    private void validateFlacHeader(final DataInputStream inputStream) throws IOException, InvalidFormatException {
        final var header = inputStream.readNBytes(4);
        if (!Arrays.equals(header, FLAC_HEADER)) {
            throw new InvalidFormatException("Not a FLAC file!");
        }
    }

    private void writeFlacHeader(final DataOutputStream outputStream) throws IOException {
        outputStream.write(FLAC_HEADER);
    }


    private BlockHeader readBlockHeader(final DataInputStream inputStream) throws IOException, InvalidFormatException{
        final var bitStream = BitInputStream.wrap(inputStream, ByteOrder.BIG_ENDIAN);

        final boolean lastBlock = bitStream.readBits(1) == 1;
        final var blockType = getBlockType((int) bitStream.readBits(7));

        final var size = bitStream.readBits(24);

        return new BlockHeader(lastBlock, blockType, (int) size);
    }

    private void writeBlockHeader(final DataOutputStream outputStream, BlockHeader header) throws IOException {
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

    private static BlockType getBlockType(final int headerByte) throws InvalidFormatException {
        return switch (headerByte) {
            case 0 -> BlockType.STREAM_INFO;
            case 1 -> BlockType.PADDING;
            case 2 -> BlockType.APPLICATION;
            case 3 -> BlockType.SEEK_TABLE;
            case 4 -> BlockType.VORBIS_COMMENT;
            case 5 -> BlockType.CUESHEET;
            case 6 -> BlockType.PICTURE;
            default -> throw new InvalidFormatException("Incorrect block type: " + headerByte);
        };
    }

}
