package org.sona.format;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Iterator;
import org.sona.exception.InvalidFormatException;
import org.sona.exception.UncheckedInvalidFormatException;

/**
 * Audio that adds up how long the frames handed out of it play, so that how long a file plays comes from its decoded
 * audio and not from a header, which can be wrong. It reads the audio once, like the audio it wraps, so it can be
 * iterated by one caller at a time, each continuing where the last stopped.
 *
 * @param <F> the kind of frame the audio gives
 */
public abstract sealed class MeasuredAudio<F extends PcmFrame> implements Iterable<F>
        permits MeasuredIntAudio, MeasuredFloatAudio {

    private final Iterator<F> frames;
    // Frames are counted in runs at one sample rate, which a stream rarely changes, so that a run's duration is worked
    // out from its whole sample count, not rounded for each frame.
    private Duration finishedRuns = Duration.ZERO;
    private int runSampleRate;
    private long runSamples;

    /**
     * @param frames the iterator of the audio, which this takes over
     */
    MeasuredAudio(final Iterator<F> frames) {
        this.frames = frames;
    }

    @Override
    public Iterator<F> iterator() {
        return new CountingIterator();
    }

    /**
     * Decodes what a caller left unread, as some read only the start of the audio.
     *
     * @throws IOException when the audio can't be read, or ends before a frame does
     * @throws InvalidFormatException when the audio is corrupt
     */
    public void readToEnd() throws IOException, InvalidFormatException {
        try {
            for (final var _ : this) {
                // Counted as it passes.
            }
        } catch (final UncheckedIOException e) {
            throw e.getCause();
        } catch (final UncheckedInvalidFormatException e) {
            throw e.getCause();
        }
    }

    /**
     * @return how long the frames read so far play
     */
    public Duration duration() {
        if (runSamples == 0) {
            return finishedRuns;
        }
        return finishedRuns.plus(AudioUtils.duration(runSamples, runSampleRate));
    }

    private void count(final F frame) {
        if (frame.sampleRate() != runSampleRate) {
            finishedRuns = duration();
            runSampleRate = frame.sampleRate();
            runSamples = 0;
        }
        runSamples += frame.sampleCount();
    }

    private final class CountingIterator implements Iterator<F> {

        @Override
        public boolean hasNext() {
            return frames.hasNext();
        }

        @Override
        public F next() {
            final var frame = frames.next();
            count(frame);
            return frame;
        }
    }
}
