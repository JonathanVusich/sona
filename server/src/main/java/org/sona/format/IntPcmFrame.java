package org.sona.format;

/**
 * A block of decoded audio as signed integers, as a lossless decoder such as FLAC's makes it.
 */
public final class IntPcmFrame implements PcmFrame {

    private final int[][] samples;
    private final int sampleRate;

    /**
     * @param samples one array per channel, all the same length, which the frame copies
     * @param sampleRate samples per second, per channel
     */
    public IntPcmFrame(final int[][] samples, final int sampleRate) {
        this.samples = new int[samples.length][];
        for (var channel = 0; channel < samples.length; channel++) {
            this.samples[channel] = samples[channel].clone();
        }
        this.sampleRate = sampleRate;
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public FrameContinuity continuity() {
        return FrameContinuity.CONTINUOUS;
    }

    @Override
    public int channelCount() {
        return samples.length;
    }

    @Override
    public int sampleCount() {
        return samples[0].length;
    }

    public int channelSample(final int channel, final int index) {
        return samples[channel][index];
    }
}
