package org.sona.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sona.exception.InvalidFormatException;
import org.sona.format.PcmAudio;
import org.sona.format.PcmFrame;

/**
 * Compares fingerprints with those {@code fpcalc} (Chromaprint 1.5.1) computed from the same audio, which the
 * reference files under {@code org/sona/fingerprint} hold.
 */
class FingerprintTest {

    @Test
    void matchesFpcalcWhenNothingIsResampled() throws IOException, InvalidFormatException {
        final var audio = new ChordAudio(11025, 1, 16, 30);

        final var fingerprint = new Fingerprint(audio);

        assertThat(fingerprint.compressed()).isEqualTo(reference("chords_11025hz_mono"));
    }

    @Test
    void matchesFpcalcForSilence() throws IOException, InvalidFormatException {
        final var audio = new SilentAudio(44100, 2, 10);

        final var fingerprint = new Fingerprint(audio);

        assertThat(fingerprint.compressed()).isEqualTo(reference("silence_44100hz_stereo"));
    }

    @ParameterizedTest
    @CsvSource({
        "44100, 16, chords_44100hz_stereo",
        "48000, 16, chords_48000hz_stereo",
        // The same audio as at 16 bits, shifted up, so it comes back the same once brought to 16 bits.
        "48000, 24, chords_48000hz_stereo"
    })
    void matchesFpcalcWhenResampled(final int sampleRate, final int bitsPerSample, final String referenceName)
            throws IOException, InvalidFormatException {
        final var audio = new ChordAudio(sampleRate, 2, bitsPerSample, 30);

        final var fingerprint = new Fingerprint(audio);

        assertThat(fingerprint.compressed()).isEqualTo(reference(referenceName));
    }

    @Test
    void audioShorterThanOneFrameHasAnEmptyFingerprint() throws IOException, InvalidFormatException {
        final var audio = new SilentAudio(44100, 2, 0);

        final var fingerprint = new Fingerprint(audio);

        assertThat(fingerprint.compressed()).isEqualTo("AQAAAA");
    }

    // Chromaprint resamples from each frame's rate, so 5 seconds at 22050 Hz then 5 at 44100 Hz make 10 seconds.
    @Test
    void resamplesFromEachFramesRateWhenItChanges() throws IOException, InvalidFormatException {
        final var audio = new RateChangingAudio(new SilentAudio(22050, 1, 5), new SilentAudio(44100, 1, 5));

        final var signal = new Signal(audio);

        assertThat(signal.sampleCount()).isCloseTo(10 * Signal.SAMPLE_RATE, within(100));
    }

    private static String reference(final String name) throws IOException {
        try (final var inputStream = FingerprintTest.class.getResourceAsStream(name + ".txt")) {
            return new String(inputStream.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    /**
     * Chords of three notes that change every 0.4 seconds, picked by a linear congruential generator: the audio
     * {@code gen.py} wrote for {@code fpcalc}, generated the same way.
     */
    private record ChordAudio(int sampleRate, int channels, int bitsPerSample, int seconds) implements PcmAudio {

        @Override
        public Iterator<PcmFrame> iterator() {
            return new FrameIterator(sampleRate, sampleRate * seconds, channels, this::sample);
        }

        private int sample(final int index, final int channel) {
            final var chord = (long) index * 5 / (2L * sampleRate);
            final var seed = (chord * 1103515245L + 12345) % (1L << 31);
            var total = 0.0;
            for (var note = 0; note < 3; note++) {
                final var pitch = 48 + ((seed >> (8 * note)) % 36);
                final var frequency = 440.0 * Math.pow(2, (pitch - 69) / 12.0);
                total += Math.sin(2 * Math.PI * frequency * index / sampleRate + channel);
            }
            return (int) Math.round(8000 * total) << (bitsPerSample - 16);
        }
    }

    // Audio whose sample rate changes where the first ends, as a stream of two joined files may.
    private record RateChangingAudio(SilentAudio first, SilentAudio second) implements PcmAudio {

        @Override
        public int sampleRate() {
            return first.sampleRate();
        }

        @Override
        public int bitsPerSample() {
            return 16;
        }

        @Override
        public Iterator<PcmFrame> iterator() {
            return Stream.concat(
                            StreamSupport.stream(first.spliterator(), false),
                            StreamSupport.stream(second.spliterator(), false))
                    .iterator();
        }
    }

    private record SilentAudio(int sampleRate, int channels, int seconds) implements PcmAudio {

        @Override
        public int bitsPerSample() {
            return 16;
        }

        @Override
        public Iterator<PcmFrame> iterator() {
            return new FrameIterator(sampleRate, sampleRate * seconds, channels, (_, _) -> 0);
        }
    }

    @FunctionalInterface
    private interface SampleSource {
        int sample(int index, int channel);
    }

    private static final class FrameIterator implements Iterator<PcmFrame> {

        private static final int FRAME_SIZE = 4096;

        private final int sampleRate;
        private final int sampleCount;
        private final int channels;
        private final SampleSource source;
        private int next;

        FrameIterator(final int sampleRate, final int sampleCount, final int channels, final SampleSource source) {
            this.sampleRate = sampleRate;
            this.sampleCount = sampleCount;
            this.channels = channels;
            this.source = source;
        }

        @Override
        public boolean hasNext() {
            return next < sampleCount;
        }

        @Override
        public PcmFrame next() {
            final var samples = new int[channels][Math.min(FRAME_SIZE, sampleCount - next)];
            for (var i = 0; i < samples[0].length; i++, next++) {
                for (var channel = 0; channel < channels; channel++) {
                    samples[channel][i] = source.sample(next, channel);
                }
            }
            return new PcmFrame(samples, sampleRate);
        }
    }
}
