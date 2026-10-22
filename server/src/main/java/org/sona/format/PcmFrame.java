package org.sona.format;

/**
 * A block of decoded audio: the same number of samples for each channel, as signed integers.
 */
public final class PcmFrame {

    private final int[][] samples;

    /**
     * @param samples one array per channel, all the same length, which the frame takes ownership of
     */
    public PcmFrame(final int[][] samples) {
        this.samples = samples;
    }

    public int channelCount() {
        return samples.length;
    }

    /**
     * @return how many samples each channel has
     */
    public int sampleCount() {
        return samples[0].length;
    }

    public int channelSample(final int channel, final int index) {
        return samples[channel][index];
    }
}
