package org.sona.fingerprint;

import java.util.Arrays;
import org.sona.format.PcmFrame;

/**
 * Resamples a stream of 16-bit mono audio, a frame at a time, with a windowed-sinc low-pass filter. The filter cuts off
 * a little below the Nyquist frequency of the lower rate, so nothing above it folds back into the output. It's worked
 * out ahead for {@link #PHASES} positions between two input samples, and each output sample takes the nearest.
 */
public final class Resampler {

    private static final int PHASES = 256;
    // Taps on each side of the filter's centre, counted in samples at the lower rate.
    private static final int HALF_WIDTH = 8;
    // How much of the band up to the lower rate's Nyquist frequency the filter passes.
    private static final double PASSBAND = 0.8;
    private static final double KAISER_BETA = 9;

    private final int inputRate;
    private final int outputRate;
    private final int halfTaps;
    private final double cutoff;
    private final double[][] filters;
    // The input samples later output samples still need, from input sample bufferStart on.
    private double[] buffer;
    private int bufferLength;
    private long bufferStart;
    private long outputCount;

    public Resampler(final int inputRate, final int outputRate) {
        this.inputRate = inputRate;
        this.outputRate = outputRate;
        final var scale = Math.min(1.0, (double) outputRate / inputRate);
        this.halfTaps = (int) Math.ceil(HALF_WIDTH / scale);
        this.cutoff = PASSBAND * scale;
        this.filters = filterTable();
        // The samples before the stream starts count as silence.
        this.buffer = new double[4 * halfTaps];
        this.bufferLength = halfTaps - 1;
        this.bufferStart = 1 - halfTaps;
    }

    /**
     * @param signal the next samples of the input, in one channel
     * @return the output samples the input so far completes, in one channel
     */
    public PcmFrame resampleFrame(final PcmFrame signal) {
        if (inputRate == outputRate) {
            return signal;
        }
        appendInput(signal);
        final var output = new int[readyOutputCount()];
        for (var i = 0; i < output.length; i++) {
            output[i] = nextOutputSample();
        }
        dropUsedInput();
        return new PcmFrame(new int[][] {output}, outputRate);
    }

    private double[][] filterTable() {
        final var table = new double[PHASES + 1][2 * halfTaps];
        for (var phase = 0; phase <= PHASES; phase++) {
            table[phase] = phaseFilter((double) phase / PHASES);
        }
        return table;
    }

    // The filter for an output sample this far past the input sample at its centre, scaled to pass silence unchanged.
    private double[] phaseFilter(final double offset) {
        final var taps = new double[2 * halfTaps];
        var sum = 0.0;
        for (var tap = 0; tap < taps.length; tap++) {
            final var distance = tap - halfTaps + 1 - offset;
            taps[tap] = sinc(cutoff * distance) * kaiserWindow(distance / halfTaps);
            sum += taps[tap];
        }
        for (var tap = 0; tap < taps.length; tap++) {
            taps[tap] /= sum;
        }
        return taps;
    }

    private static double sinc(final double x) {
        if (x == 0) {
            return 1;
        }
        return Math.sin(Math.PI * x) / (Math.PI * x);
    }

    private static double kaiserWindow(final double position) {
        final var inside = Math.max(0, 1 - position * position);
        return besselI0(KAISER_BETA * Math.sqrt(inside)) / besselI0(KAISER_BETA);
    }

    // The zeroth-order modified Bessel function of the first kind, summed as its power series.
    private static double besselI0(final double x) {
        var sum = 1.0;
        var term = 1.0;
        for (var k = 1; term > 1e-12 * sum; k++) {
            final var factor = x / (2 * k);
            term *= factor * factor;
            sum += term;
        }
        return sum;
    }

    private void appendInput(final PcmFrame signal) {
        final var count = signal.sampleCount();
        if (bufferLength + count > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(2 * buffer.length, bufferLength + count));
        }
        for (var i = 0; i < count; i++) {
            buffer[bufferLength + i] = signal.channelSample(0, i);
        }
        bufferLength += count;
    }

    // An output sample is ready once the input reaches the end of its filter.
    private int readyOutputCount() {
        final var lastCentre = bufferStart + bufferLength - halfTaps;
        if (lastCentre <= 0) {
            return 0;
        }
        final var readyCount = Math.ceilDiv(lastCentre * outputRate, inputRate);
        return (int) Math.max(0, readyCount - outputCount);
    }

    private int nextOutputSample() {
        final var position = outputCount * inputRate;
        final var centre = Math.floorDiv(position, outputRate);
        final var phase = (int) Math.round((double) (position - centre * outputRate) * PHASES / outputRate);
        final var filter = filters[phase];
        final var first = (int) (centre - halfTaps + 1 - bufferStart);
        var sum = 0.0;
        for (var tap = 0; tap < filter.length; tap++) {
            sum += filter[tap] * buffer[first + tap];
        }
        outputCount++;
        return Math.clamp(Math.round(sum), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    // Drops the input samples before the first one the next output sample needs.
    private void dropUsedInput() {
        final var nextCentre = Math.floorDiv(outputCount * inputRate, outputRate);
        final var used = (int) (nextCentre - halfTaps + 1 - bufferStart);
        System.arraycopy(buffer, used, buffer, 0, bufferLength - used);
        bufferLength -= used;
        bufferStart += used;
    }
}
