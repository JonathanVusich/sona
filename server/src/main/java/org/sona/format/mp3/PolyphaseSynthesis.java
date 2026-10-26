package org.sona.format.mp3;

/**
 * The polyphase synthesis filterbank, which turns each instant's 32 subband samples into 32 PCM samples (ISO/IEC
 * 11172-3 section 2.4.3.2, Figure A.2): the subband samples are matrixed into 64 values pushed onto a 1024-value
 * history, 512 of which are weighted by the synthesis window and summed in 16s. The decoder keeps one per channel, as
 * each instant uses the history of the instants before.
 */
final class PolyphaseSynthesis {

    private static final int SUBBANDS = Spectrum.SUBBANDS;
    private static final int VALUES = 2 * SUBBANDS;
    private static final int HISTORY = 16 * VALUES;
    private static final int SUMMED = SynthesisWindow.SIZE / SUBBANDS;

    // N[i][k] = cos((16 + i) * (2k + 1) * pi / 64).
    private static final double[][] MATRIX = matrix();

    // A Layer II frame's 36 instants are the most synthesized at once.
    private static final int MAX_INSTANTS = 36;

    private final double[] history = new double[HISTORY];
    private final double[] windowed = new double[SynthesisWindow.SIZE];
    private final double[] pcm = new double[MAX_INSTANTS * SUBBANDS];

    /**
     * Synthesizes 32 samples from each instant's 32 subband samples: a Layer III granule's 18 instants, or a Layer I
     * or II frame's 12 or 36.
     */
    void synthesize(final SubbandSamples subbandSamples, final int instants) {
        for (var instant = 0; instant < instants; instant++) {
            pushValues(subbandSamples, instant);
            windowHistory();
            for (var sample = 0; sample < SUBBANDS; sample++) {
                var sum = 0.0;
                for (var step = 0; step < SUMMED; step++) {
                    sum += windowed[sample + SUBBANDS * step];
                }
                pcm[instant * SUBBANDS + sample] = sum;
            }
        }
    }

    /**
     * @return the sample at the index of those last synthesized, from -1 to 1 at full scale
     */
    double sample(final int index) {
        return pcm[index];
    }

    // Shifts the history along by 64, and matrixes the instant's subband samples into its first 64 values.
    private void pushValues(final SubbandSamples subbandSamples, final int instant) {
        System.arraycopy(history, 0, history, VALUES, HISTORY - VALUES);
        for (var value = 0; value < VALUES; value++) {
            var sum = 0.0;
            for (var subband = 0; subband < SUBBANDS; subband++) {
                sum += MATRIX[value][subband] * subbandSamples.subbandSample(instant, subband);
            }
            history[value] = sum;
        }
    }

    // Of each 128 values of the history, the first 32 and last 32 are weighted, in order, by the window.
    private void windowHistory() {
        for (var block = 0; block < SynthesisWindow.SIZE / VALUES; block++) {
            for (var value = 0; value < SUBBANDS; value++) {
                final var first = block * VALUES + value;
                final var second = first + SUBBANDS;
                windowed[first] = history[2 * block * VALUES + value] * SynthesisWindow.coefficient(first);
                windowed[second] =
                        history[2 * block * VALUES + 3 * SUBBANDS + value] * SynthesisWindow.coefficient(second);
            }
        }
    }

    private static double[][] matrix() {
        final var matrix = new double[VALUES][SUBBANDS];
        for (var value = 0; value < VALUES; value++) {
            for (var subband = 0; subband < SUBBANDS; subband++) {
                matrix[value][subband] = Math.cos((16 + value) * (2 * subband + 1) * Math.PI / 64);
            }
        }
        return matrix;
    }
}
