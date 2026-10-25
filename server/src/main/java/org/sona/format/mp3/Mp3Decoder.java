package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.sona.exception.InvalidFormatException;
import org.sona.format.PcmAudio;
import org.sona.format.PcmFrame;

/**
 * The audio of an MP3, which {@link Mp3Parser#parse} opens in its {@link ParsedMp3}. Its samples will be decoded to
 * 16 bits, as Layer III has no bit depth of its own.
 *
 * <p>Each frame is read from the file's stream in turn: its header, through the parser, its side information (ISO/IEC
 * 11172-3 section 2.4.1.7, ISO/IEC 13818-3 section 2.4.1.7), and its main data, which may start in the frames before
 * it. The header and side information are whole bytes, so the bit stream holds no bits after them, and the stream
 * skips the CRC and reads the main data.
 */
public final class Mp3Decoder implements PcmAudio {

    private static final int BITS_PER_SAMPLE = 16;
    // A granule has 576 values, so at most 288 pairs.
    private static final int MAX_BIG_VALUES = 288;
    // MPEG-2's scale factors use pre-emphasis from this scalefac_compress on, except in intensity stereo's right
    // channel.
    private static final int LSF_PREFLAG_COMPRESS = 500;

    private final Mp3Parser parser;
    private final Mp3StreamInfo streamInfo;
    private final InputStream input;
    private final BitInputStream bits;
    private final BitReservoir reservoir = new BitReservoir();

    /**
     * @param parser the parser that reads each frame's header
     * @param input the file's stream, at its first frame of audio
     */
    Mp3Decoder(final Mp3Parser parser, final Mp3StreamInfo streamInfo, final InputStream input) {
        this.parser = parser;
        this.streamInfo = streamInfo;
        this.input = input;
        this.bits = BitInputStream.wrap(input, ByteOrder.BIG_ENDIAN);
    }

    public Mp3StreamInfo streamInfo() {
        return streamInfo;
    }

    @Override
    public int sampleRate() {
        return streamInfo.sampleRate();
    }

    @Override
    public int bitsPerSample() {
        return BITS_PER_SAMPLE;
    }

    /**
     * @throws UnsupportedOperationException always, until Sona decodes Layer III
     */
    @Override
    public Iterator<PcmFrame> iterator() {
        // TODO: Decode Layer III frames (ISO/IEC 11172-3 and 13818-3) from the channel the file was parsed from.
        throw new UnsupportedOperationException("MP3 audio can't be decoded yet!");
    }

    /**
     * @return the next frame, whose main data must be read before the next frame is
     */
    // TODO: Read free-format frames, whose length the stream gives rather than their headers.
    Mp3Frame readFrame() throws IOException, InvalidFormatException {
        final var header = parser.readAudioFrameHeader(bits);
        // The CRC isn't checked: a frame that fails it is still decoded, as other decoders do.
        input.skipNBytes(header.protection().crcSize());
        final var sideInfo = readSideInfo(header);
        return reservoir.readMainData(input, header, sideInfo);
    }

    private SideInfo readSideInfo(final FrameHeader header) throws IOException, InvalidFormatException {
        final var mainDataBegin = (int) bits.readBits(mainDataBeginBits(header.version()));
        // The private bits are the encoder's own.
        bits.readBits(privateBits(header));
        final var channelSharing = new ArrayList<Integer>();
        for (var channel = 0; channel < header.channelCount(); channel++) {
            channelSharing.add(readChannelSharing(header.version()));
        }
        final var granules = new ArrayList<List<GranuleChannel>>();
        for (var granule = 0; granule < header.granuleCount(); granule++) {
            final var channels = new ArrayList<GranuleChannel>();
            for (var channel = 0; channel < header.channelCount(); channel++) {
                channels.add(readGranuleChannel(header, channel, granuleSharing(granule, channelSharing.get(channel))));
            }
            granules.add(List.copyOf(channels));
        }
        return new SideInfo(mainDataBegin, List.copyOf(granules));
    }

    private int mainDataBeginBits(final MpegVersion version) {
        return switch (version) {
            case MPEG_1 -> 9;
            case MPEG_2, MPEG_2_5 -> 8;
        };
    }

    private int privateBits(final FrameHeader header) {
        return switch (header.channelMode()) {
            case MONO -> monoPrivateBits(header.version());
            case STEREO, JOINT_STEREO, DUAL_CHANNEL -> stereoPrivateBits(header.version());
        };
    }

    private int monoPrivateBits(final MpegVersion version) {
        return switch (version) {
            case MPEG_1 -> 5;
            case MPEG_2, MPEG_2_5 -> 1;
        };
    }

    private int stereoPrivateBits(final MpegVersion version) {
        return switch (version) {
            case MPEG_1 -> 3;
            case MPEG_2, MPEG_2_5 -> 2;
        };
    }

    // MPEG-1's scfsi, four bits per channel, which only the second granule uses. MPEG-2 has none.
    private int readChannelSharing(final MpegVersion version) throws IOException {
        return switch (version) {
            case MPEG_1 -> (int) bits.readBits(4);
            case MPEG_2, MPEG_2_5 -> 0;
        };
    }

    // The first granule has no granule before it to share with.
    private int granuleSharing(final int granule, final int channelSharing) {
        if (granule == 0) {
            return 0;
        }
        return channelSharing;
    }

    /**
     * @param channel 0 for the left or only channel, and 1 for the right
     * @param sharing MPEG-1's scfsi for the second granule, or 0 otherwise
     */
    private GranuleChannel readGranuleChannel(final FrameHeader header, final int channel, final int sharing)
            throws IOException, InvalidFormatException {
        final var part23Length = (int) bits.readBits(12);
        final var bigValues = (int) bits.readBits(9);
        if (bigValues > MAX_BIG_VALUES) {
            throw new InvalidFormatException("MP3 granule has %d pairs of big values!".formatted(bigValues));
        }
        final var globalGain = (int) bits.readBits(8);
        final var scalefacCompress = (int) bits.readBits(scalefacCompressBits(header.version()));
        final var windowing = readWindowing();
        final var preflag = readPreflag(header, channel, scalefacCompress);
        final var scalefacScale = (int) bits.readBits(1);
        final var count1Code = count1Code(bits.readBits(1));
        return new GranuleChannel(
                part23Length,
                bigValues,
                globalGain,
                scalefacCompress,
                windowing,
                preflag,
                scalefacScale,
                count1Code,
                sharing);
    }

    private int scalefacCompressBits(final MpegVersion version) {
        return switch (version) {
            case MPEG_1 -> 4;
            case MPEG_2, MPEG_2_5 -> 9;
        };
    }

    // The window_switching_flag says which kind follows.
    private Windowing readWindowing() throws IOException, InvalidFormatException {
        if (bits.readBits(1) == 0) {
            return readNormalWindowing();
        }
        return readSwitchedWindowing();
    }

    private Windowing readNormalWindowing() throws IOException {
        final var table0 = readTable();
        final var table1 = readTable();
        final var table2 = readTable();
        final var region0Count = (int) bits.readBits(4);
        final var region1Count = (int) bits.readBits(3);
        return new Windowing.Normal(table0, table1, table2, region0Count, region1Count);
    }

    private Windowing readSwitchedWindowing() throws IOException, InvalidFormatException {
        final var blockType = BlockType.fromSwitchedBits((int) bits.readBits(2));
        final var layout = layout(blockType, bits.readBits(1));
        final var table0 = readTable();
        final var table1 = readTable();
        final var subblockGains = new ArrayList<Integer>();
        for (var window = 0; window < Windowing.WINDOWS; window++) {
            subblockGains.add((int) bits.readBits(3));
        }
        return new Windowing.Switched(blockType, layout, table0, table1, List.copyOf(subblockGains));
    }

    private int readTable() throws IOException {
        return (int) bits.readBits(5);
    }

    // The mixed block flag only mixes short blocks.
    private BlockLayout layout(final BlockType blockType, final long mixedBlockFlag) {
        if (blockType != BlockType.SHORT) {
            return BlockLayout.LONG;
        }
        if (mixedBlockFlag == 0) {
            return BlockLayout.SHORT;
        }
        return BlockLayout.MIXED;
    }

    private int readPreflag(final FrameHeader header, final int channel, final int scalefacCompress)
            throws IOException {
        return switch (header.version()) {
            case MPEG_1 -> (int) bits.readBits(1);
            case MPEG_2, MPEG_2_5 -> lsfPreflag(header, channel, scalefacCompress);
        };
    }

    private int lsfPreflag(final FrameHeader header, final int channel, final int scalefacCompress) {
        if (header.isIntensityStereo() && channel == 1) {
            return 0;
        }
        if (scalefacCompress >= LSF_PREFLAG_COMPRESS) {
            return 1;
        }
        return 0;
    }

    private HuffmanCode count1Code(final long count1TableSelect) {
        if (count1TableSelect == 0) {
            return HuffmanTables.COUNT1_A;
        }
        return HuffmanTables.COUNT1_B;
    }
}
