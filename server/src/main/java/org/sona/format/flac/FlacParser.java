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

    static final int MEDIA_CATALOG_NUMBER_SIZE = 128;
    static final int ISRC_SIZE = 12;

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
        final var data = inputStream.readNBytes(header.size() - 4);
        return new Application(header, applicationId, data);
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
        final var mediaCatalogNumber = readAscii(inputStream, MEDIA_CATALOG_NUMBER_SIZE);
        final var leadInSamples = inputStream.readLong();

        final var bitInputStream = BitInputStream.wrap(inputStream, ByteOrder.BIG_ENDIAN);
        final var compactDisc = bitInputStream.readBits(1) == 1;
        // Reserved: the rest of this byte plus 258 more
        bitInputStream.readBits(7);
        inputStream.skipNBytes(258);

        final var numberOfTracks = inputStream.readUnsignedByte();
        final var tracks = new ArrayList<CueSheetTrack>(numberOfTracks);
        for (int i = 0; i < numberOfTracks; i++) {
            tracks.add(readCueSheetTrack(inputStream));
        }

        return new CueSheet(header, mediaCatalogNumber, leadInSamples, compactDisc, tracks);
    }

    private CueSheetTrack readCueSheetTrack(final DataInputStream inputStream) throws IOException {
        final var offset = inputStream.readLong();
        final var number = inputStream.readUnsignedByte();
        final var isrc = readAscii(inputStream, ISRC_SIZE);

        final var bitInputStream = BitInputStream.wrap(inputStream, ByteOrder.BIG_ENDIAN);
        // The track type bit is 0 for audio and 1 for anything else.
        final var audio = bitInputStream.readBits(1) == 0;
        final var preEmphasis = bitInputStream.readBits(1) == 1;
        // Reserved: the rest of this byte plus 13 more
        bitInputStream.readBits(6);
        inputStream.skipNBytes(13);

        final var numberOfIndexPoints = inputStream.readUnsignedByte();
        final var indexPoints = new ArrayList<CueSheetIndexPoint>(numberOfIndexPoints);
        for (int i = 0; i < numberOfIndexPoints; i++) {
            final var indexOffset = inputStream.readLong();
            final var indexNumber = inputStream.readUnsignedByte();
            // Reserved
            inputStream.skipNBytes(3);

            indexPoints.add(new CueSheetIndexPoint(indexOffset, indexNumber));
        }

        return new CueSheetTrack(offset, number, isrc, audio, preEmphasis, indexPoints);
    }

    private Picture readPicture(final DataInputStream inputStream, final BlockHeader header) throws IOException {
        // Unlike Vorbis comment lengths, picture lengths are big endian.
        final var pictureType = inputStream.readInt();
        final var mediaTypeLength = inputStream.readInt();
        final var mediaType = new String(inputStream.readNBytes(mediaTypeLength), StandardCharsets.US_ASCII);
        final var descriptionLength = inputStream.readInt();
        final var description = readUtf8(inputStream, descriptionLength);

        final var width = inputStream.readInt();
        final var height = inputStream.readInt();
        final var colorDepth = inputStream.readInt();
        final var numberOfColors = inputStream.readInt();

        final var dataLength = inputStream.readInt();
        final var data = inputStream.readNBytes(dataLength);

        return new Picture(header, pictureType, mediaType, description, width, height, colorDepth, numberOfColors, data);
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

    // Fixed-size ASCII fields are padded with NUL bytes, or are all NUL when empty.
    private String readAscii(final DataInputStream inputStream, final int size) throws IOException {
        final var bytes = inputStream.readNBytes(size);
        var length = 0;
        while (length < size && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
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
