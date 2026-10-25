package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.IOException;
import java.util.Arrays;
import org.sona.exception.InvalidFormatException;

/**
 * A granule's 576 frequency lines for one channel: its Huffman-coded values, requantized (ISO/IEC 11172-3 sections
 * 2.4.3.4.4 to 2.4.3.4.7). A short block's lines are in the order they're coded: by band, then window. The decoder
 * keeps one per channel, and reads each granule into it in place, as it runs for every granule of the file.
 */
final class Spectrum {

    static final int LINES = FrameHeader.GRANULE_SIZE;
    // The filterbank's 32 subbands each hold 18 lines.
    static final int SUBBANDS = 32;
    static final int SUBBAND_LINES = 18;

    // The global gain of 210 leaves a value unscaled.
    private static final int GAIN_OFFSET = 210;
    // Each subblock gain step is 2 in 2^(1/4) steps of the global gain: a factor of 4.
    private static final int SUBBLOCK_GAIN_STEP = 8;
    // The largest value a big-value table codes is 15 plus 13 linbits.
    private static final double[] POW_4_3 = pow43(15 + (1 << 13) - 1);
    private static final int LINBITS_VALUE = 15;
    private static final int QUAD = 4;
    // The most bits the bit stream reads at once is 57.
    private static final int MAX_READ_BITS = 56;

    private final double[] lines = new double[LINES];
    // What each line's value is multiplied by: 2 to the power of a quarter of its gain, less its scale factor.
    private final double[] scales = new double[LINES];
    // One short band's lines as they're reordered.
    private final double[] reorderBuffer = new double[LINES];
    private int nonZeroEnd;

    /**
     * Reads the granule's values for the channel, which follow its scale factors, and the bits after them that its
     * part2_3_length takes, so that the bit stream is left where the next channel's bits start.
     */
    void read(
            final BitInputStream bits,
            final FrameHeader header,
            final GranuleChannel granuleChannel,
            final ScaleFactors scaleFactors)
            throws IOException, InvalidFormatException {
        final var part3Length = granuleChannel.part23Length() - scaleFactors.part2Length();
        if (part3Length < 0) {
            throw new InvalidFormatException("MP3 granule's scale factors run past its part2_3_length!");
        }
        computeScales(header, granuleChannel, scaleFactors);
        final var bigValuesEnd = Math.min(2 * granuleChannel.bigValues(), LINES);
        final var bigValueBits = readBigValues(bits, header, granuleChannel, bigValuesEnd);
        if (bigValueBits > part3Length) {
            throw new InvalidFormatException("MP3 granule's big values run past its part2_3_length!");
        }
        nonZeroEnd = readCount1(bits, granuleChannel.count1Code(), bigValuesEnd, part3Length - bigValueBits);
        Arrays.fill(lines, nonZeroEnd, LINES, 0);
    }

    /**
     * Makes every line 0, for a granule whose values can't be read.
     */
    void clear() {
        Arrays.fill(lines, 0);
        nonZeroEnd = 0;
    }

    double line(final int index) {
        return lines[index];
    }

    void setLine(final int index, final double value) {
        lines[index] = value;
    }

    /**
     * @return the line after the last one that may not be 0
     */
    int nonZeroEnd() {
        return nonZeroEnd;
    }

    /**
     * @param end the line after the last one that may not be 0, such as after stereo processing spreads one channel's
     *     lines to the other
     */
    void setNonZeroEnd(final int end) {
        nonZeroEnd = end;
    }

    /**
     * Reorders a short or mixed block's short bands from the order they're coded in, each window's lines in turn, to
     * the order the IMDCT takes them in: each line's three windows in turn (ISO/IEC 11172-3 section 2.4.3.4.8).
     */
    void reorderShortBands(final FrameHeader header, final Windowing windowing) {
        final var layout = windowing.layout();
        if (layout == BlockLayout.LONG) {
            return;
        }
        final var bands = ScaleFactorBands.fromSampleRate(header.sampleRate());
        final var firstBand = firstShortBand(layout);
        var start = Windowing.WINDOWS * bands.shortBandStart(firstBand);
        for (var band = firstBand; band < ScaleFactorBands.SHORT_BANDS; band++) {
            final var width = bands.shortBandWidth(band);
            for (var line = 0; line < width; line++) {
                for (var window = 0; window < Windowing.WINDOWS; window++) {
                    reorderBuffer[Windowing.WINDOWS * line + window] = lines[start + window * width + line];
                }
            }
            System.arraycopy(reorderBuffer, 0, lines, start, Windowing.WINDOWS * width);
            start += Windowing.WINDOWS * width;
        }
        // Lines move up from the windows above, so any may now not be 0.
        nonZeroEnd = LINES;
    }

    /**
     * Undoes the aliasing the encoder's filterbank put between neighbouring subbands, with butterflies across each
     * boundary of the long blocks' subbands (ISO/IEC 11172-3 section 2.4.3.4.9).
     */
    void reduceAliasing(final Windowing windowing) {
        final var boundaries = switch (windowing.layout()) {
            case LONG -> SUBBANDS - 1;
            case MIXED -> 1;
            case SHORT -> 0;
        };
        for (var boundary = 1; boundary <= boundaries; boundary++) {
            final var first = SUBBAND_LINES * boundary;
            for (var butterfly = 0; butterfly < AliasCoefficients.COUNT; butterfly++) {
                final var below = lines[first - 1 - butterfly];
                final var above = lines[first + butterfly];
                final var cs = AliasCoefficients.cs(butterfly);
                final var ca = AliasCoefficients.ca(butterfly);
                lines[first - 1 - butterfly] = below * cs - above * ca;
                lines[first + butterfly] = above * cs + below * ca;
            }
        }
    }

    private void computeScales(
            final FrameHeader header, final GranuleChannel granuleChannel, final ScaleFactors scaleFactors) {
        final var bands = ScaleFactorBands.fromSampleRate(header.sampleRate());
        final var gain = granuleChannel.globalGain() - GAIN_OFFSET;
        final var shift = granuleChannel.scalefacScale() + 1;
        final var longEnd = longBandsEnd(header, granuleChannel.windowing().layout());
        for (var band = 0; band < longEnd; band++) {
            final var factor =
                    scaleFactors.longFactor(band) + granuleChannel.preflag() * ScaleFactorTables.pretab(band);
            fillScale(bands.longBandStart(band), bands.longBandWidth(band), gain - (factor << shift));
        }
        switch (granuleChannel.windowing()) {
            case Windowing.Normal _ -> {}
            case Windowing.Switched switched -> {
                if (switched.layout() != BlockLayout.LONG) {
                    computeShortScales(bands, switched, gain, shift, scaleFactors);
                }
            }
        }
    }

    // After its long bands, a short or mixed block's short bands each hold three windows, one after another.
    private void computeShortScales(
            final ScaleFactorBands bands,
            final Windowing.Switched switched,
            final int gain,
            final int shift,
            final ScaleFactors scaleFactors) {
        final var firstBand = firstShortBand(switched.layout());
        var line = 3 * bands.shortBandStart(firstBand);
        for (var band = firstBand; band < ScaleFactorBands.SHORT_BANDS; band++) {
            final var width = bands.shortBandWidth(band);
            for (var window = 0; window < Windowing.WINDOWS; window++) {
                final var windowGain =
                        gain - SUBBLOCK_GAIN_STEP * switched.subblockGains().get(window);
                fillScale(line, width, windowGain - (scaleFactors.shortFactor(band, window) << shift));
                line += width;
            }
        }
    }

    // The exponent is in steps of 2^(1/4).
    private void fillScale(final int start, final int width, final int exponent) {
        Arrays.fill(scales, start, start + width, Math.pow(2, exponent / 4.0));
    }

    private static int longBandsEnd(final FrameHeader header, final BlockLayout layout) {
        return switch (layout) {
            case LONG -> ScaleFactorBands.LONG_BANDS;
            case SHORT -> 0;
            case MIXED -> header.version().mixedLongBands();
        };
    }

    private static int firstShortBand(final BlockLayout layout) {
        return switch (layout) {
            case LONG, SHORT -> 0;
            case MIXED -> ScaleFactors.MIXED_FIRST_SHORT_BAND;
        };
    }

    /**
     * Reads the big values, which come in up to three regions, each coded with its own table.
     *
     * @return how many bits they take
     */
    private int readBigValues(
            final BitInputStream bits, final FrameHeader header, final GranuleChannel granuleChannel, final int end)
            throws IOException, InvalidFormatException {
        final var bands = ScaleFactorBands.fromSampleRate(header.sampleRate());
        return switch (granuleChannel.windowing()) {
            case Windowing.Normal normal -> readNormalRegions(bits, bands, normal, end);
            case Windowing.Switched switched -> readSwitchedRegions(bits, bands, switched, end);
        };
    }

    // The regions end at the long bands the side information gives.
    private int readNormalRegions(
            final BitInputStream bits, final ScaleFactorBands bands, final Windowing.Normal normal, final int end)
            throws IOException, InvalidFormatException {
        final var region1 = Math.min(bands.longBandStart(normal.region0Count() + 1), end);
        final var region2Band =
                Math.min(normal.region0Count() + normal.region1Count() + 2, ScaleFactorBands.LONG_BANDS);
        final var region2 = Math.max(Math.min(bands.longBandStart(region2Band), end), region1);
        return readRegion(bits, HuffmanTables.bigValueTable(normal.table0()), 0, region1)
                + readRegion(bits, HuffmanTables.bigValueTable(normal.table1()), region1, region2)
                + readRegion(bits, HuffmanTables.bigValueTable(normal.table2()), region2, end);
    }

    // The first region takes 8 long bands, or 3 short bands of three windows, and the second takes the rest.
    private int readSwitchedRegions(
            final BitInputStream bits, final ScaleFactorBands bands, final Windowing.Switched switched, final int end)
            throws IOException, InvalidFormatException {
        final var region1Start = switch (switched.blockType()) {
            case SHORT -> 3 * bands.shortBandStart(3);
            case NORMAL, START, STOP -> bands.longBandStart(8);
        };
        final var region1 = Math.min(region1Start, end);
        return readRegion(bits, HuffmanTables.bigValueTable(switched.table0()), 0, region1)
                + readRegion(bits, HuffmanTables.bigValueTable(switched.table1()), region1, end);
    }

    // Each codeword codes a pair of values, x and y.
    private int readRegion(final BitInputStream bits, final BigValueTable table, final int start, final int end)
            throws IOException {
        var bitsRead = 0;
        for (var line = start; line < end; line += 2) {
            final var pair = table.code().readValue(bits);
            bitsRead += table.code().codeLength(pair)
                    + readValue(bits, table, pair / table.size(), line)
                    + readValue(bits, table, pair % table.size(), line + 1);
        }
        return bitsRead;
    }

    // A value of 15 in a table with linbits is followed by that many bits to add to it, and a value that isn't 0 by its
    // sign. Returns how many bits those take.
    private int readValue(final BitInputStream bits, final BigValueTable table, final int magnitude, final int line)
            throws IOException {
        if (magnitude == 0) {
            lines[line] = 0;
            return 0;
        }
        var value = magnitude;
        var linbits = 0;
        if (magnitude == LINBITS_VALUE && table.linbits() > 0) {
            linbits = table.linbits();
            value += (int) bits.readBits(linbits);
        }
        lines[line] = signed(bits, POW_4_3[value] * scales[line]);
        return linbits + 1;
    }

    // Count1 values come four at a time, each 0 or 1 with a sign bit for each 1, until the channel's bits run out. Some
    // encoders write a last quadruple that runs past them, which decoders drop, so it's left unread. The channel's bits
    // may end with some its values don't use, which are skipped, so the next channel's bits start where they're read.
    private int readCount1(final BitInputStream bits, final HuffmanCode code, final int start, final int available)
            throws IOException {
        var line = start;
        var left = available;
        while (line + QUAD <= LINES && left > 0) {
            final var quad = code.peekValue(bits);
            final var quadBits = code.codeLength(quad) + Integer.bitCount(quad);
            if (quadBits > left) {
                break;
            }
            code.readValue(bits);
            for (var value = 0; value < QUAD; value++) {
                lines[line + value] = readCount1Value(bits, quad >> (QUAD - 1 - value) & 1, line + value);
            }
            left -= quadBits;
            line += QUAD;
        }
        skipBits(bits, left);
        return line;
    }

    private double readCount1Value(final BitInputStream bits, final int magnitude, final int line) throws IOException {
        if (magnitude == 0) {
            return 0;
        }
        return signed(bits, scales[line]);
    }

    // A sign bit of 1 is negative.
    private static double signed(final BitInputStream bits, final double value) throws IOException {
        if (bits.readBits(1) == 1) {
            return -value;
        }
        return value;
    }

    private static void skipBits(final BitInputStream bits, final int count) throws IOException {
        for (var left = count; left > 0; left -= MAX_READ_BITS) {
            bits.readBits(Math.min(left, MAX_READ_BITS));
        }
    }

    private static double[] pow43(final int maxValue) {
        final var powers = new double[maxValue + 1];
        for (var value = 0; value <= maxValue; value++) {
            powers[value] = Math.pow(value, 4.0 / 3);
        }
        return powers;
    }
}
