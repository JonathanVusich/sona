package org.sona.format;

/**
 * A block of decoded audio: the same number of samples for each channel, as signed integers. A decoder may keep one
 * frame and rewrite it for each frame it decodes, so a frame holds its samples only until the next is read.
 */
public final class PcmFrame {

    private final int[][] samples;
    private int start;
    private int end;

    /**
     * @param samples one array per channel, all the same length, which the frame takes ownership of; the frame holds
     *     all of them until {@link #setRange} says otherwise
     */
    public PcmFrame(final int[][] samples) {
        this.samples = samples;
        this.end = samples[0].length;
    }

    /**
     * Makes the frame hold only each channel's samples from {@code start} up to {@code end}, such as when a decoder
     * trims its encoder's delay from the first frame.
     */
    public void setRange(final int start, final int end) {
        this.start = start;
        this.end = end;
    }

    public int channelCount() {
        return samples.length;
    }

    /**
     * @return how many samples each channel has
     */
    public int sampleCount() {
        return end - start;
    }

    public int channelSample(final int channel, final int index) {
        return samples[channel][start + index];
    }
}
