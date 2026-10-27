package org.sona.fingerprint;

import java.util.Arrays;
import java.util.Iterator;
import org.sona.format.FrameContinuity;
import org.sona.format.PcmFrame;

/**
 * Brings audio a decoder made in floating point to Chromaprint's 16-bit mono samples at 11025 Hz, as {@code fpcalc}
 * does with libswresample: each stretch of audio with one sample rate and
 * channel count has its channels resampled on their own ({@link Resampler}) and mixed in floating point, and only
 * then are the samples rounded to 16 bits.
 */
final class FloatSignalReader {

    private static final int SAMPLE_RATE = Signal.SAMPLE_RATE;
    // Enough samples past the last that a stretch's end, which reflects the audio, doesn't change any that are kept.
    private static final int MARGIN = 1000;
    private static final float FULL_SCALE = 32768;

    private final int maxSamples;

    FloatSignalReader(final int maxSamples) {
        this.maxSamples = maxSamples;
    }

    /**
     * @param first the first frame, which the decoder may rewrite when the next is read
     * @param rest the frames after it
     * @return at most {@code maxSamples} samples
     */
    int[] read(final PcmFrame first, final Iterator<PcmFrame> rest) {
        var stretch = new Stretch(first.sampleRate(), first.channelCount());
        var finished = new float[0][];
        var finishedCount = 0;
        var frame = first;
        while (true) {
            if (frame.sampleRate() != stretch.sampleRate() || frame.channelCount() != stretch.channelCount()) {
                finished = Arrays.copyOf(finished, finished.length + 1);
                finished[finished.length - 1] = stretch.resampled();
                finishedCount += finished[finished.length - 1].length;
                stretch = new Stretch(frame.sampleRate(), frame.channelCount());
            }
            stretch.add(frame);
            if (finishedCount + stretch.outputEstimate() >= maxSamples + MARGIN || !rest.hasNext()) {
                break;
            }
            frame = rest.next();
            if (frame.continuity() == FrameContinuity.AFTER_DECODING_ERROR) {
                break;
            }
        }
        finished = Arrays.copyOf(finished, finished.length + 1);
        finished[finished.length - 1] = stretch.resampled();
        return toSixteenBits(finished);
    }

    private int[] toSixteenBits(final float[][] stretches) {
        final var samples = new int[maxSamples];
        var length = 0;
        for (final var stretch : stretches) {
            for (var index = 0; index < stretch.length && length < maxSamples; index++) {
                final var rounded = Math.rint(stretch[index] * FULL_SCALE);
                samples[length++] = (int) Math.clamp(rounded, Short.MIN_VALUE, Short.MAX_VALUE);
            }
        }
        return Arrays.copyOf(samples, length);
    }

    /**
     * Audio with one sample rate and channel count. As libswresample does when it takes channels away, each channel
     * is resampled on its own, and the resampled channels are mixed to mono.
     */
    private static final class Stretch {

        private final int sampleRate;
        private final int channelCount;
        private float[][] channels;
        private int length;

        Stretch(final int sampleRate, final int channelCount) {
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
            this.channels = new float[channelCount][1 << 16];
        }

        int sampleRate() {
            return sampleRate;
        }

        int channelCount() {
            return channelCount;
        }

        /**
         * @return how many samples at 11025 Hz the audio so far will make
         */
        long outputEstimate() {
            return (long) length * SAMPLE_RATE / sampleRate;
        }

        void add(final PcmFrame frame) {
            if (length + frame.sampleCount() > channels[0].length) {
                final var size = Math.max(channels[0].length * 2, length + frame.sampleCount());
                for (var channel = 0; channel < channelCount; channel++) {
                    channels[channel] = Arrays.copyOf(channels[channel], size);
                }
            }
            for (var channel = 0; channel < channelCount; channel++) {
                for (var index = 0; index < frame.sampleCount(); index++) {
                    channels[channel][length + index] = frame.channelExactSample(channel, index);
                }
            }
            length += frame.sampleCount();
        }

        float[] resampled() {
            final var resampled = new float[channelCount][];
            for (var channel = 0; channel < channelCount; channel++) {
                resampled[channel] = resampledChannel(channels[channel]);
            }
            return mixed(resampled);
        }

        private float[] resampledChannel(final float[] samples) {
            if (sampleRate == SAMPLE_RATE) {
                return Arrays.copyOf(samples, length);
            }
            return new Resampler(sampleRate, SAMPLE_RATE).resampleEnded(samples, length);
        }

        // swresample's mix to mono: each channel of stereo counts half, and with more channels, an equal share.
        private float[] mixed(final float[][] resampled) {
            if (channelCount == 1) {
                return resampled[0];
            }
            final var mono = new float[resampled[0].length];
            for (var index = 0; index < mono.length; index++) {
                mono[index] = mixedSample(resampled, index);
            }
            return mono;
        }

        private float mixedSample(final float[][] resampled, final int index) {
            if (channelCount == 2) {
                return 0.5f * resampled[0][index] + 0.5f * resampled[1][index];
            }
            var sum = 0f;
            for (var channel = 0; channel < channelCount; channel++) {
                sum += resampled[channel][index];
            }
            return sum / channelCount;
        }
    }
}
