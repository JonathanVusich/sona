package org.sona.format;

/**
 * A block of decoded audio: the same number of samples for each channel, as signed integers, and for a decoder that
 * works in floating point, also the samples it made before they were rounded to integers. A decoder may keep one frame
 * and rewrite it for each frame it decodes, so a frame holds its samples only until the next is read.
 */
public final class PcmFrame {

    private static final float[][] NO_EXACT_SAMPLES = new float[0][];

    private final int[][] samples;
    private final float[][] exactSamples;
    private int sampleRate;
    private FrameContinuity continuity = FrameContinuity.CONTINUOUS;
    private int start;
    private int end;

    /**
     * @param samples one array per channel, all the same length, which the frame takes ownership of; the frame holds
     *     all of them until {@link #setRange} says otherwise
     * @param sampleRate samples per second, per channel, which a stream may change between frames
     */
    public PcmFrame(final int[][] samples, final int sampleRate) {
        this(samples, NO_EXACT_SAMPLES, sampleRate);
    }

    /**
     * @param samples the samples, rounded to integers as {@link #PcmFrame(int[][], int)} takes them
     * @param exactSamples the same samples before they were rounded, as floating-point numbers where a full-scale
     *     sample is 1.0, in the same layout, which the frame takes ownership of too
     */
    public PcmFrame(final int[][] samples, final float[][] exactSamples, final int sampleRate) {
        this.samples = samples;
        this.exactSamples = exactSamples;
        this.sampleRate = sampleRate;
        this.end = samples[0].length;
    }

    /**
     * Gives the frame the rate of the frame a decoder decoded into it.
     */
    public void setSampleRate(final int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /**
     * Says whether the decoder dropped a frame it couldn't decode before this one.
     */
    public void setContinuity(final FrameContinuity continuity) {
        this.continuity = continuity;
    }

    public FrameContinuity continuity() {
        return continuity;
    }

    /**
     * @return samples per second, per channel
     */
    public int sampleRate() {
        return sampleRate;
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

    /**
     * @return whether the decoder made the samples in floating point, which {@link #channelExactSample} gives
     */
    public boolean hasExactSamples() {
        return exactSamples.length > 0;
    }

    /**
     * @return the sample before it was rounded to an integer
     * @throws ArrayIndexOutOfBoundsException when the frame has no exact samples
     */
    public float channelExactSample(final int channel, final int index) {
        return exactSamples[channel][start + index];
    }
}
