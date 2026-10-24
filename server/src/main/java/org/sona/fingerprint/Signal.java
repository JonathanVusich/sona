package org.sona.fingerprint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.sona.exception.InvalidFormatException;
import org.sona.exception.UncheckedInvalidFormatException;
import org.sona.format.PcmAudio;
import org.sona.format.PcmFrame;

/**
 * The start of a file's audio as Chromaprint reads it: 16-bit mono samples at 11025 Hz, the first two minutes at most.
 */
public final class Signal {

    public static final int SAMPLE_RATE = 11025;

    private static final int MAX_SAMPLES = 120 * SAMPLE_RATE;
    private static final int BITS_PER_SAMPLE = 16;

    private final int[] samples;

    /**
     * Decodes only as much of the audio as it needs.
     *
     * @throws IOException when the audio can't be read, or ends before a frame does
     * @throws InvalidFormatException when the audio is corrupt
     */
    public Signal(final PcmAudio audio) throws IOException, InvalidFormatException {
        try {
            this.samples = readSamples(audio);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (UncheckedInvalidFormatException e) {
            throw e.getCause();
        }
    }

    public int sampleCount() {
        return samples.length;
    }

    public int sample(final int index) {
        return samples[index];
    }

    private static int[] readSamples(final PcmAudio audio) {
        final var resampler = new Resampler(audio.sampleRate(), SAMPLE_RATE);
        final var samples = new int[MAX_SAMPLES];
        var length = 0;
        for (final var frame : audio) {
            final var resampled = resampler.resampleFrame(downmix(frame, audio.bitsPerSample()));
            final var copied = Math.min(resampled.sampleCount(), MAX_SAMPLES - length);
            for (var i = 0; i < copied; i++) {
                samples[length + i] = resampled.channelSample(0, i);
            }
            length += copied;
            if (length == MAX_SAMPLES) {
                break;
            }
        }
        return Arrays.copyOf(samples, length);
    }

    // Brings each sample to 16 bits, as an audio library converting for Chromaprint would, then averages the channels.
    private static PcmFrame downmix(final PcmFrame frame, final int bitsPerSample) {
        final var mono = new int[frame.sampleCount()];
        for (var i = 0; i < mono.length; i++) {
            var sum = 0L;
            for (var channel = 0; channel < frame.channelCount(); channel++) {
                sum += toSixteenBits(frame.channelSample(channel, i), bitsPerSample);
            }
            mono[i] = (int) (sum / frame.channelCount());
        }
        return new PcmFrame(new int[][] {mono});
    }

    private static int toSixteenBits(final int sample, final int bitsPerSample) {
        if (bitsPerSample >= BITS_PER_SAMPLE) {
            return sample >> (bitsPerSample - BITS_PER_SAMPLE);
        }
        return sample << (BITS_PER_SAMPLE - bitsPerSample);
    }
}
