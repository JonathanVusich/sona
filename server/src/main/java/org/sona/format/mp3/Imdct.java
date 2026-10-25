package org.sona.format.mp3;

import java.util.Arrays;

/**
 * The inverse MDCT of Layer III's hybrid filterbank, for one channel (ISO/IEC 11172-3 section 2.4.3.4.10): each
 * subband's 18 lines become 36 windowed samples, whose first half is added to the second half of the subband's last
 * block. A long block is one 36-point transform, and a short block three 12-point ones that overlap within it. The
 * decoder keeps one per channel, as each granule overlaps the one before.
 */
final class Imdct {

    private static final int SUBBANDS = Spectrum.SUBBANDS;
    private static final int LINES = Spectrum.SUBBAND_LINES;
    private static final int BLOCK = 2 * LINES;
    private static final int SHORT_LINES = LINES / Windowing.WINDOWS;
    private static final int SHORT_BLOCK = 2 * SHORT_LINES;
    // A mixed block's two lowest subbands are long blocks with the normal window, whatever its block type.
    private static final int MIXED_LONG_SUBBANDS = 2;

    private static final double[][] LONG_COSINES = cosines(BLOCK, LINES);
    private static final double[][] SHORT_COSINES = cosines(SHORT_BLOCK, SHORT_LINES);
    private static final double[] NORMAL_WINDOW = window(BlockType.NORMAL);
    private static final double[] START_WINDOW = window(BlockType.START);
    private static final double[] STOP_WINDOW = window(BlockType.STOP);
    private static final double[] SHORT_WINDOW = shortWindow();

    // Each subband's second half of its last block, which the next block's first half is added to.
    private final double[] overlap = new double[SUBBANDS * LINES];
    private final double[] block = new double[BLOCK];
    // The 18 samples of each of the 32 subbands: sample t of subband s at t * 32 + s, as the synthesis filterbank takes
    // each instant's subbands together.
    private final double[] subbandSamples = new double[SUBBANDS * LINES];

    /**
     * Transforms a granule's spectrum, which must have been reordered and had its aliasing reduced.
     */
    void transform(final Spectrum spectrum, final Windowing windowing) {
        for (var subband = 0; subband < SUBBANDS; subband++) {
            final var blockType = subbandBlockType(windowing, subband);
            if (blockType == BlockType.SHORT) {
                transformShort(spectrum, subband);
            } else {
                transformLong(spectrum, subband, longWindow(blockType));
            }
            overlapAdd(subband);
        }
    }

    /**
     * @param instant from 0 to 17
     * @return the subband's sample at the instant, from the granule last transformed
     */
    double subbandSample(final int instant, final int subband) {
        return subbandSamples[instant * SUBBANDS + subband];
    }

    private static BlockType subbandBlockType(final Windowing windowing, final int subband) {
        if (windowing.mixedBlock() && subband < MIXED_LONG_SUBBANDS) {
            return BlockType.NORMAL;
        }
        return windowing.blockType();
    }

    private void transformLong(final Spectrum spectrum, final int subband, final double[] window) {
        final var first = subband * LINES;
        for (var sample = 0; sample < BLOCK; sample++) {
            var sum = 0.0;
            for (var line = 0; line < LINES; line++) {
                sum += spectrum.line(first + line) * LONG_COSINES[sample][line];
            }
            block[sample] = sum * window[sample];
        }
    }

    // After reordering, each line's three windows come in turn. Each window's 12 samples start 6 after the last's,
    // from sample 6, so the block's first and last 6 are 0.
    private void transformShort(final Spectrum spectrum, final int subband) {
        Arrays.fill(block, 0);
        final var first = subband * LINES;
        for (var window = 0; window < Windowing.WINDOWS; window++) {
            for (var sample = 0; sample < SHORT_BLOCK; sample++) {
                var sum = 0.0;
                for (var line = 0; line < SHORT_LINES; line++) {
                    sum += spectrum.line(first + Windowing.WINDOWS * line + window) * SHORT_COSINES[sample][line];
                }
                block[SHORT_LINES * (window + 1) + sample] += sum * SHORT_WINDOW[sample];
            }
        }
    }

    // Odd subbands come out of the encoder's filterbank inverted in frequency, which negating every other sample
    // undoes.
    private void overlapAdd(final int subband) {
        final var first = subband * LINES;
        for (var sample = 0; sample < LINES; sample++) {
            var value = block[sample] + overlap[first + sample];
            if (subband % 2 == 1 && sample % 2 == 1) {
                value = -value;
            }
            subbandSamples[sample * SUBBANDS + subband] = value;
            overlap[first + sample] = block[LINES + sample];
        }
    }

    private static double[] longWindow(final BlockType blockType) {
        return switch (blockType) {
            case NORMAL, SHORT -> NORMAL_WINDOW;
            case START -> START_WINDOW;
            case STOP -> STOP_WINDOW;
        };
    }

    // cos(pi / (2 * size) * (2 * sample + 1 + size / 2) * (2 * line + 1)), for a transform of size samples.
    private static double[][] cosines(final int size, final int lines) {
        final var cosines = new double[size][lines];
        for (var sample = 0; sample < size; sample++) {
            for (var line = 0; line < lines; line++) {
                cosines[sample][line] = Math.cos(Math.PI / (2 * size) * (2 * sample + 1 + size / 2) * (2 * line + 1));
            }
        }
        return cosines;
    }

    // The long windows of block types 0, 1 and 3: a start block's second half, and a stop block's first, are flat
    // where they meet a short block's window, and fall with its slope.
    private static double[] window(final BlockType blockType) {
        final var window = new double[BLOCK];
        for (var sample = 0; sample < BLOCK; sample++) {
            window[sample] = switch (blockType) {
                case NORMAL, SHORT -> longSlope(sample);
                case START -> startWindow(sample);
                case STOP -> startWindow(BLOCK - 1 - sample);
            };
        }
        return window;
    }

    private static double startWindow(final int sample) {
        if (sample < LINES) {
            return longSlope(sample);
        }
        if (sample < LINES + SHORT_LINES) {
            return 1;
        }
        if (sample < LINES + 2 * SHORT_LINES) {
            return Math.sin(Math.PI / SHORT_BLOCK * (sample - LINES + 0.5));
        }
        return 0;
    }

    private static double longSlope(final int sample) {
        return Math.sin(Math.PI / BLOCK * (sample + 0.5));
    }

    private static double[] shortWindow() {
        final var window = new double[SHORT_BLOCK];
        for (var sample = 0; sample < SHORT_BLOCK; sample++) {
            window[sample] = Math.sin(Math.PI / SHORT_BLOCK * (sample + 0.5));
        }
        return window;
    }
}
