package org.sona.format;

import java.util.Arrays;

/**
 * A block of decoded audio as floating-point numbers, where a full-scale sample is 1.0, as a decoder that works in
 * floating point makes it, before they're rounded to integers.
 */
public final class FloatPcmFrame implements PcmFrame {

    private final float[][] samples;
    private final int sampleRate;
    private final FrameContinuity continuity;

    /**
     * @param samples one array per channel, from which the frame copies the samples from {@code start} up to
     *     {@code end}, such as when a decoder trims its encoder's delay from the first frame
     * @param sampleRate samples per second, per channel
     * @param continuity whether the decoder dropped a frame it couldn't decode before this one
     */
    public FloatPcmFrame(
            final float[][] samples,
            final int start,
            final int end,
            final int sampleRate,
            final FrameContinuity continuity) {
        this.samples = new float[samples.length][];
        for (var channel = 0; channel < samples.length; channel++) {
            this.samples[channel] = Arrays.copyOfRange(samples[channel], start, end);
        }
        this.sampleRate = sampleRate;
        this.continuity = continuity;
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public FrameContinuity continuity() {
        return continuity;
    }

    @Override
    public int channelCount() {
        return samples.length;
    }

    @Override
    public int sampleCount() {
        return samples[0].length;
    }

    public float channelSample(final int channel, final int index) {
        return samples[channel][index];
    }
}
