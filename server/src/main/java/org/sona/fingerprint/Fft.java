package org.sona.fingerprint;

/**
 * A radix-2 fast Fourier transform of real frames of one size, a power of two. It reuses its working arrays from frame
 * to frame, so one transform isn't safe to share between threads.
 */
public final class Fft {

    private final int size;
    private final double[] cosines;
    private final double[] sines;
    private final int[] reversedIndices;
    private final double[] real;
    private final double[] imaginary;

    public Fft(final int size) {
        this.size = size;
        this.cosines = new double[size / 2];
        this.sines = new double[size / 2];
        for (var i = 0; i < size / 2; i++) {
            cosines[i] = Math.cos(2 * Math.PI * i / size);
            sines[i] = Math.sin(2 * Math.PI * i / size);
        }
        this.reversedIndices = reversedIndices(size);
        this.real = new double[size];
        this.imaginary = new double[size];
    }

    /**
     * @param frame {@code size} samples
     * @return the frame's power at each frequency from 0 up to half the sample rate
     */
    public PowerSpectrum powerSpectrum(final double[] frame) {
        for (var i = 0; i < size; i++) {
            real[reversedIndices[i]] = frame[i];
            imaginary[i] = 0;
        }
        for (var length = 2; length <= size; length *= 2) {
            combineHalves(length);
        }
        final var powers = new double[size / 2 + 1];
        for (var i = 0; i < powers.length; i++) {
            powers[i] = real[i] * real[i] + imaginary[i] * imaginary[i];
        }
        return new PowerSpectrum(powers);
    }

    // Combines each pair of transforms of half this length into one of this length.
    private void combineHalves(final int length) {
        final var half = length / 2;
        final var step = size / length;
        for (var start = 0; start < size; start += length) {
            for (var i = 0; i < half; i++) {
                final var even = start + i;
                final var odd = even + half;
                final var cos = cosines[i * step];
                final var sin = sines[i * step];
                final var oddReal = real[odd] * cos + imaginary[odd] * sin;
                final var oddImaginary = imaginary[odd] * cos - real[odd] * sin;
                real[odd] = real[even] - oddReal;
                imaginary[odd] = imaginary[even] - oddImaginary;
                real[even] += oddReal;
                imaginary[even] += oddImaginary;
            }
        }
    }

    // Where each sample goes, so that combining halves leaves the frequencies in order.
    private static int[] reversedIndices(final int size) {
        final var bits = Integer.numberOfTrailingZeros(size);
        final var indices = new int[size];
        for (var i = 0; i < size; i++) {
            indices[i] = Integer.reverse(i) >>> (Integer.SIZE - bits);
        }
        return indices;
    }
}
