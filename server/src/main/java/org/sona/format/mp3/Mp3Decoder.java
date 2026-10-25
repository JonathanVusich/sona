package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.sona.exception.InvalidFormatException;
import org.sona.exception.UncheckedInvalidFormatException;
import org.sona.format.PcmAudio;
import org.sona.format.PcmFrame;

/**
 * The audio of an MP3, which {@link Mp3Parser#parse} opens in its {@link ParsedMp3}, decoded a frame at a time as it's
 * iterated, to 16 bits, as Layer III has no bit depth of its own. Only the samples that were encoded are given: the
 * encoder's delay and padding, and the decoder's own delay, are left out, as a LAME tag says. Every frame is decoded
 * into the same {@link PcmFrame}, which holds its samples until the next is read.
 *
 * <p>The iterator throws {@link UncheckedIOException} when the stream can't be read, and
 * {@link UncheckedInvalidFormatException} when a frame is corrupt.
 *
 * <p>Each granule's scale factors and spectrum are decoded for each channel, then its joint stereo, and each channel's
 * reordering, alias reduction, IMDCT and synthesis. The decoder keeps each channel's state from granule to granule,
 * and decodes every frame into the same buffers: only what it reads from the frame is new for each one.
 *
 * <p>Each frame is read from the file's stream in turn: its header, through the parser, its side information (ISO/IEC
 * 11172-3 section 2.4.1.7, ISO/IEC 13818-3 section 2.4.1.7), and its main data, which may start in the frames before
 * it. The header and side information are whole bytes, so the bit stream holds no bits after them, and the stream
 * skips the CRC and reads the main data.
 *
 * <p>Scale factors start each granule's channel in the main data (ISO/IEC 11172-3 section 2.4.2.7, ISO/IEC 13818-3
 * section 2.4.3.2). Both versions code them in the same order, which is then laid out by band. A mixed block's lowest
 * bands are long: 8 in MPEG-1, and 6 in MPEG-2, and its short bands start at band 3.
 */
public final class Mp3Decoder implements PcmAudio {

    private static final int BITS_PER_SAMPLE = 16;
    private static final int MAX_CHANNELS = 2;
    private static final double FULL_SCALE = 32768;
    // A granule has 576 values, so at most 288 pairs.
    private static final int MAX_BIG_VALUES = 288;
    // MPEG-2's scale factors use pre-emphasis from this scalefac_compress on, except in intensity stereo's right
    // channel.
    private static final int LSF_PREFLAG_COMPRESS = 500;
    // MPEG-1's short blocks have scale factors for 12 bands, in each window.
    private static final int MPEG_1_SHORT_FACTORS = 12 * Windowing.WINDOWS;
    // How many scale factors take slen1 bits, the rest taking slen2: bands 0 to 10 of a long block, and 0 to 5 of a
    // short one.
    private static final int MPEG_1_LONG_SLEN1_FACTORS = 11;
    private static final int MPEG_1_SHORT_SLEN1_FACTORS = 6 * Windowing.WINDOWS;
    // A mixed block has 8 long bands, then short bands 3 to 11, of which 3 to 5 take slen1 bits.
    private static final int MPEG_1_MIXED_FACTORS = 8 + 9 * Windowing.WINDOWS;
    private static final int MPEG_1_MIXED_SLEN1_FACTORS = 8 + 3 * Windowing.WINDOWS;
    // The groups of long bands MPEG-1's second granule may share with its first: bands 0 to 20.
    private static final int[] SHARED_GROUP_STARTS = {0, 6, 11, 16, 21};
    private static final int FIRST_GROUP_BIT = 0b1000;
    // MPEG-1's intensity positions run from 0 to 6, and 7 says a band isn't intensity coded.
    private static final int MPEG_1_INTENSITY_LIMIT = 7;
    // MPEG-2's right channel of an intensity stereo frame halves scalefac_compress, then splits it at these.
    private static final int LSF_INTENSITY_SPLIT_1 = 180;
    private static final int LSF_INTENSITY_SPLIT_2 = 244;
    // Its other channels split it at these.
    private static final int LSF_SPLIT_1 = 400;
    private static final int LSF_SPLIT_2 = 500;

    private final Mp3Parser parser;
    private final Mp3StreamInfo streamInfo;
    private final InputStream input;
    private final BitInputStream bits;
    private final BitReservoir reservoir = new BitReservoir();
    // Each channel's scale factors for the last granule read, which stereo decoding takes and MPEG-1's second granule
    // may share.
    private final ScaleFactors[] scaleFactors = {ScaleFactors.NONE, ScaleFactors.NONE};
    private final Spectrum[] spectra = {new Spectrum(), new Spectrum()};
    private final StereoProcessor stereoProcessor = new StereoProcessor();
    private final Imdct[] imdcts = {new Imdct(), new Imdct()};
    private final PolyphaseSynthesis[] syntheses = {new PolyphaseSynthesis(), new PolyphaseSynthesis()};
    private final int[][] samples;
    private final PcmFrame pcmFrame;
    private boolean iterated;
    private long framesDecoded;

    /**
     * @param parser the parser that reads each frame's header
     * @param input the file's stream, at its first frame of audio
     */
    Mp3Decoder(final Mp3Parser parser, final Mp3StreamInfo streamInfo, final InputStream input) {
        this.parser = parser;
        this.streamInfo = streamInfo;
        this.input = input;
        this.bits = BitInputStream.wrap(input, ByteOrder.BIG_ENDIAN);
        this.samples = new int[streamInfo.channelCount()][streamInfo.samplesPerFrame()];
        this.pcmFrame = new PcmFrame(samples);
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
     * @throws IllegalStateException when called a second time
     */
    @Override
    public Iterator<PcmFrame> iterator() {
        if (iterated) {
            throw new IllegalStateException("MP3 decoder can only be iterated once!");
        }
        iterated = true;
        return new FrameIterator();
    }

    private final class FrameIterator implements Iterator<PcmFrame> {

        @Override
        public boolean hasNext() {
            return framesDecoded < framesWithSamples();
        }

        @Override
        public PcmFrame next() {
            if (!hasNext()) {
                throw new NoSuchElementException("MP3 stream has no more frames!");
            }
            try {
                return readPcmFrame();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            } catch (final InvalidFormatException e) {
                throw new UncheckedInvalidFormatException(e);
            }
        }
    }

    // The frames up to the last sample given, which a LAME tag's padding may leave short of the stream's end.
    private long framesWithSamples() {
        final var lastSample = streamInfo.leadingSamples() + streamInfo.sampleCount();
        final var samplesPerFrame = streamInfo.samplesPerFrame();
        return Math.min(Math.ceilDiv(lastSample, samplesPerFrame), streamInfo.frameCount());
    }

    // Frames whose samples all come before the first one given are still decoded, as each frame's state carries on to
    // the next.
    private PcmFrame readPcmFrame() throws IOException, InvalidFormatException {
        while (true) {
            final var firstSample = framesDecoded * streamInfo.samplesPerFrame();
            final var frame = decodeFrame();
            framesDecoded++;
            final var start = Math.max(streamInfo.leadingSamples() - firstSample, 0);
            final var end = Math.min(
                    streamInfo.leadingSamples() + streamInfo.sampleCount() - firstSample, streamInfo.samplesPerFrame());
            if (start < end) {
                frame.setRange((int) start, (int) end);
                return frame;
            }
        }
    }

    /**
     * @return the frame's 16-bit samples, in the frame the decoder rewrites for each frame it decodes
     * @throws InvalidFormatException when the frame has more or fewer channels than the stream's first
     */
    private PcmFrame decodeFrame() throws IOException, InvalidFormatException {
        final var frame = readFrame();
        final var header = frame.header();
        if (header.channelCount() != samples.length) {
            throw new InvalidFormatException("MP3 frame changes the stream's channel count!");
        }
        checkGranulesFit(frame);
        for (var granule = 0; granule < header.granuleCount(); granule++) {
            readSpectra(frame, granule);
            final var sideInfo = frame.sideInfo();
            if (header.channelCount() == MAX_CHANNELS) {
                stereoProcessor.processStereo(
                        header,
                        spectra[0],
                        spectra[1],
                        sideInfo.granules().get(granule).get(1),
                        scaleFactors[1]);
            }
            for (var channel = 0; channel < header.channelCount(); channel++) {
                synthesizeGranule(
                        header, sideInfo.granules().get(granule).get(channel).windowing(), channel);
                toSamples(channel, granule * FrameHeader.GRANULE_SIZE);
            }
        }
        pcmFrame.setRange(0, header.version().getSamplesPerFrame());
        return pcmFrame;
    }

    // Each channel's bits start with its scale factors, and its values follow, until its part2_3_length is used up.
    private void readSpectra(final Mp3Frame frame, final int granule) throws IOException, InvalidFormatException {
        final var header = frame.header();
        for (var channel = 0; channel < header.channelCount(); channel++) {
            final var granuleChannel = frame.sideInfo().granules().get(granule).get(channel);
            final var channelFactors = readScaleFactors(frame, granule, channel);
            spectra[channel].read(frame.mainData(), header, granuleChannel, channelFactors);
        }
    }

    // The main data is padded with zeros, which a frame whose granules take more bits than it holds would read.
    private void checkGranulesFit(final Mp3Frame frame) throws InvalidFormatException {
        final var granuleBits = frame.sideInfo().granules().stream()
                .flatMap(List::stream)
                .mapToInt(GranuleChannel::part23Length)
                .sum();
        if (granuleBits > frame.mainDataLength() * Byte.SIZE) {
            throw new InvalidFormatException("MP3 frame's granules take more bits than its main data holds!");
        }
    }

    private void synthesizeGranule(final FrameHeader header, final Windowing windowing, final int channel) {
        final var spectrum = spectra[channel];
        spectrum.reorderShortBands(header, windowing);
        spectrum.reduceAliasing(windowing);
        imdcts[channel].transform(spectrum, windowing);
        syntheses[channel].synthesize(imdcts[channel]);
    }

    // Rounded to the nearest 16-bit value, half to even, and clipped to the 16-bit range.
    private void toSamples(final int channel, final int first) {
        final var synthesis = syntheses[channel];
        for (var sample = 0; sample < FrameHeader.GRANULE_SIZE; sample++) {
            final var value = Math.rint(synthesis.sample(sample) * FULL_SCALE);
            samples[channel][first + sample] = (int) Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE);
        }
    }

    /**
     * @return the next frame, whose main data must be read before the next frame is
     */
    Mp3Frame readFrame() throws IOException, InvalidFormatException {
        final var header = parser.readAudioFrameHeader(bits);
        // TODO: Decode free-format frames, whose length the stream gives rather than their headers.
        if (header.bitrate() == FrameHeader.FREE_FORMAT_BITRATE) {
            throw new InvalidFormatException("Free-format MP3s can't be decoded yet!");
        }
        // The CRC isn't checked: a frame that fails it is still decoded, as other decoders do.
        input.skipNBytes(header.protection().crcSize());
        final var sideInfo = readSideInfo(header);
        return reservoir.readMainData(input, header, sideInfo);
    }

    /**
     * Reads a channel's scale factors for a granule of the frame last read, from its main data, which must be at the
     * start of the granule's channel. MPEG-1's second granule may keep the first's for groups of long bands, so it must
     * be read after the first granule's.
     *
     * @param channel 0 for the left or only channel, and 1 for the right
     */
    ScaleFactors readScaleFactors(final Mp3Frame frame, final int granule, final int channel) throws IOException {
        final var header = frame.header();
        final var granuleChannel = frame.sideInfo().granules().get(granule).get(channel);
        scaleFactors[channel] = switch (header.version()) {
            case MPEG_1 -> readMpeg1Factors(frame.mainData(), granuleChannel, scaleFactors[channel]);
            case MPEG_2, MPEG_2_5 -> readLsfFactors(frame.mainData(), header, granuleChannel, channel);
        };
        return scaleFactors[channel];
    }

    private ScaleFactors readMpeg1Factors(
            final BitInputStream mainData, final GranuleChannel granuleChannel, final ScaleFactors previous)
            throws IOException {
        final var compress = granuleChannel.scalefacCompress();
        final var slen1 = ScaleFactorTables.mpeg1Slen1(compress);
        final var slen2 = ScaleFactorTables.mpeg1Slen2(compress);
        final var layout = granuleChannel.windowing().layout();
        return switch (layout) {
            case LONG -> readMpeg1LongFactors(mainData, granuleChannel.scaleFactorSharing(), previous, slen1, slen2);
            case SHORT ->
                readMpeg1ShortFactors(mainData, layout, MPEG_1_SHORT_FACTORS, MPEG_1_SHORT_SLEN1_FACTORS, slen1, slen2);
            case MIXED ->
                readMpeg1ShortFactors(mainData, layout, MPEG_1_MIXED_FACTORS, MPEG_1_MIXED_SLEN1_FACTORS, slen1, slen2);
        };
    }

    // Each group whose scfsi bit is set keeps the first granule's scale factors, and the rest are read. The first
    // group's bit is the highest.
    private ScaleFactors readMpeg1LongFactors(
            final BitInputStream mainData,
            final int sharing,
            final ScaleFactors previous,
            final int slen1,
            final int slen2)
            throws IOException {
        final var factors = new int[ScaleFactors.SIZE];
        final var intensityLimits = new int[ScaleFactors.SIZE];
        var part2Length = 0;
        for (var group = 0; group < SHARED_GROUP_STARTS.length - 1; group++) {
            final var shared = (sharing & (FIRST_GROUP_BIT >> group)) != 0;
            for (var band = SHARED_GROUP_STARTS[group]; band < SHARED_GROUP_STARTS[group + 1]; band++) {
                intensityLimits[band] = MPEG_1_INTENSITY_LIMIT;
                if (shared) {
                    factors[band] = previous.longFactor(band);
                } else {
                    final var slen = slen(band, MPEG_1_LONG_SLEN1_FACTORS, slen1, slen2);
                    factors[band] = readFactor(mainData, slen);
                    part2Length += slen;
                }
            }
        }
        return new ScaleFactors(factors, intensityLimits, part2Length);
    }

    private ScaleFactors readMpeg1ShortFactors(
            final BitInputStream mainData,
            final BlockLayout layout,
            final int count,
            final int slen1Count,
            final int slen1,
            final int slen2)
            throws IOException {
        final var factors = new int[ScaleFactors.SIZE];
        final var intensityLimits = new int[ScaleFactors.SIZE];
        var part2Length = 0;
        for (var index = 0; index < count; index++) {
            final var slen = slen(index, slen1Count, slen1, slen2);
            final var slot = slot(index, layout, MpegVersion.MPEG_1.mixedLongBands());
            factors[slot] = readFactor(mainData, slen);
            intensityLimits[slot] = MPEG_1_INTENSITY_LIMIT;
            part2Length += slen;
        }
        return new ScaleFactors(factors, intensityLimits, part2Length);
    }

    private int slen(final int index, final int slen1Count, final int slen1, final int slen2) {
        if (index < slen1Count) {
            return slen1;
        }
        return slen2;
    }

    // Each partition's scale factors take the same number of bits, and the largest they can hold is the illegal
    // intensity position (ISO/IEC 13818-3 section 2.4.3.2).
    private ScaleFactors readLsfFactors(
            final BitInputStream mainData,
            final FrameHeader header,
            final GranuleChannel granuleChannel,
            final int channel)
            throws IOException {
        final var lengths = lsfLengths(header, channel, granuleChannel.scalefacCompress());
        final var layout = granuleChannel.windowing().layout();
        final var mixedLongBands = header.version().mixedLongBands();
        final var factors = new int[ScaleFactors.SIZE];
        final var intensityLimits = new int[ScaleFactors.SIZE];
        var part2Length = 0;
        var index = 0;
        for (var partition = 0; partition < ScaleFactorTables.LSF_PARTITIONS; partition++) {
            final var count = ScaleFactorTables.lsfBandCount(lengths.table(), layout, partition);
            final var slen = lengths.slens().get(partition);
            for (var factor = 0; factor < count; factor++) {
                final var slot = slot(index++, layout, mixedLongBands);
                factors[slot] = readFactor(mainData, slen);
                intensityLimits[slot] = (1 << slen) - 1;
                part2Length += slen;
            }
        }
        return new ScaleFactors(factors, intensityLimits, part2Length);
    }

    /**
     * @param channel 0 for the left or only channel, and 1 for the right
     */
    private LsfLengths lsfLengths(final FrameHeader header, final int channel, final int scalefacCompress) {
        if (header.isIntensityStereo() && channel == 1) {
            return intensityLsfLengths(scalefacCompress >> 1);
        }
        return lsfLengths(scalefacCompress);
    }

    private LsfLengths lsfLengths(final int compress) {
        if (compress < LSF_SPLIT_1) {
            return new LsfLengths(
                    0, List.of((compress >> 4) / 5, (compress >> 4) % 5, (compress & 15) >> 2, compress & 3));
        }
        if (compress < LSF_SPLIT_2) {
            final var offset = compress - LSF_SPLIT_1;
            return new LsfLengths(1, List.of((offset >> 2) / 5, (offset >> 2) % 5, offset & 3, 0));
        }
        final var offset = compress - LSF_SPLIT_2;
        return new LsfLengths(2, List.of(offset / 3, offset % 3, 0, 0));
    }

    private LsfLengths intensityLsfLengths(final int compress) {
        if (compress < LSF_INTENSITY_SPLIT_1) {
            return new LsfLengths(3, List.of(compress / 36, compress % 36 / 6, compress % 36 % 6, 0));
        }
        if (compress < LSF_INTENSITY_SPLIT_2) {
            final var offset = compress - LSF_INTENSITY_SPLIT_1;
            return new LsfLengths(4, List.of((offset & 63) >> 4, (offset & 15) >> 2, offset & 3, 0));
        }
        final var offset = compress - LSF_INTENSITY_SPLIT_2;
        return new LsfLengths(5, List.of(offset / 3, offset % 3, 0, 0));
    }

    // A scale factor of no bits is 0.
    private int readFactor(final BitInputStream mainData, final int slen) throws IOException {
        if (slen == 0) {
            return 0;
        }
        return (int) mainData.readBits(slen);
    }

    // Where the scale factor coded at the index goes: the layout's long bands come first, then its short bands by band
    // and window.
    private int slot(final int index, final BlockLayout layout, final int mixedLongBands) {
        final var longCount = switch (layout) {
            case LONG -> ScaleFactors.SIZE;
            case SHORT -> 0;
            case MIXED -> mixedLongBands;
        };
        if (index < longCount) {
            return index;
        }
        return ScaleFactorBands.LONG_BANDS + firstShortBand(layout) * Windowing.WINDOWS + index - longCount;
    }

    private int firstShortBand(final BlockLayout layout) {
        return switch (layout) {
            case LONG, SHORT -> 0;
            case MIXED -> ScaleFactors.MIXED_FIRST_SHORT_BAND;
        };
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
        final var mixedBlock = bits.readBits(1) == 1;
        final var table0 = readTable();
        final var table1 = readTable();
        final var subblockGains = new ArrayList<Integer>();
        for (var window = 0; window < Windowing.WINDOWS; window++) {
            subblockGains.add((int) bits.readBits(3));
        }
        return new Windowing.Switched(blockType, mixedBlock, table0, table1, List.copyOf(subblockGains));
    }

    private int readTable() throws IOException {
        return (int) bits.readBits(5);
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
