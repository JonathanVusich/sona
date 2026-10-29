package org.sona.format;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import org.sona.exception.InvalidFormatException;
import org.sona.exception.UncheckedInvalidFormatException;

@UtilityClass
public final class AudioUtils {

    /**
     * @param samples per channel
     * @return how long that many samples play at the sample rate
     */
    public static Duration duration(final long samples, final int sampleRate) {
        final var nanos = (samples % sampleRate) * 1_000_000_000L / sampleRate;
        return Duration.ofSeconds(samples / sampleRate, nanos);
    }

    /**
     * Decodes all of the audio, so that how long it plays comes from its frames and not from a header, which can be
     * wrong. Frames are counted in runs at one sample rate, which a stream rarely changes, so that a run's duration is
     * worked out from its whole sample count, not rounded for each frame.
     *
     * @throws IOException when the audio can't be read, or ends before a frame does
     * @throws InvalidFormatException when the audio is corrupt
     */
    public static Duration duration(final Iterable<? extends PcmFrame> frames)
            throws IOException, InvalidFormatException {
        var finishedRuns = Duration.ZERO;
        var runSampleRate = 0;
        var runSamples = 0L;
        try {
            for (final var frame : frames) {
                if (frame.sampleRate() != runSampleRate) {
                    if (runSamples > 0) {
                        finishedRuns = finishedRuns.plus(duration(runSamples, runSampleRate));
                    }
                    runSampleRate = frame.sampleRate();
                    runSamples = 0;
                }
                runSamples += frame.sampleCount();
            }
        } catch (final UncheckedIOException e) {
            throw e.getCause();
        } catch (final UncheckedInvalidFormatException e) {
            throw e.getCause();
        }
        return runSamples == 0 ? finishedRuns : finishedRuns.plus(duration(runSamples, runSampleRate));
    }
}
