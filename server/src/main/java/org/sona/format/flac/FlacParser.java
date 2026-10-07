package org.sona.format.flac;

import dev.javax.bitstream.BitInputStream;
import org.sona.exception.InvalidFormatException;
import org.sona.format.Format;
import org.sona.format.MD5Checksum;
import org.sona.format.Parser;
import org.sona.metadata.RawMetadata;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class FlacParser implements Parser {

    static final byte[] FLAC_HEADER = "fLaC".getBytes(StandardCharsets.US_ASCII);

    @Override
    public Format format() {
        return Format.FLAC;
    }

    @Override
    public RawMetadata parse(final InputStream inputStream) throws IOException, InvalidFormatException {
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

        final var rawMetadata = new RawMetadata(duration(streamInfo));

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

        return rawMetadata;
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

    private BlockHeader readBlockHeader(final DataInputStream inputStream) throws IOException, InvalidFormatException{
        final var bitStream = BitInputStream.wrap(inputStream, ByteOrder.BIG_ENDIAN);

        final boolean lastBlock = bitStream.readBits(1) == 1;
        final var blockType = getBlockType((int) bitStream.readBits(7));

        final var size = bitStream.readBits(24);

        return new BlockHeader(lastBlock, blockType, (int) size);
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
