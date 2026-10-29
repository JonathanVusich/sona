package org.sona.samples;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import org.sona.exception.InvalidFormatException;
import org.sona.format.flac.FlacParser;
import org.sona.format.flac.FlacWriter;
import org.sona.metadata.RawMetadata;

/**
 * Tiny synthetic FLAC files for tests, written by {@link FlacWriter}.
 */
@UtilityClass
public final class FlacFiles {

    // How long the silence is that the files' audio repeats.
    private static final Duration AUDIO_DURATION = Duration.ofSeconds(10);

    /**
     * @param metadata a whole number of tens of seconds long
     * @return the metadata written as FLAC, followed by the audio frames of {@link FlacAudioSample#SILENCE_10_SECONDS}
     * as many times as fit the duration, which the {@link FlacWriter}'s stream info matches, so the file decodes and can
     * be fingerprinted. Frames repeat as they are: the decoder doesn't read their frame numbers.
     */
    public static byte[] flacFile(final RawMetadata metadata) {
        final var repeats = metadata.duration().dividedBy(AUDIO_DURATION);
        if (repeats < 1 || !AUDIO_DURATION.multipliedBy(repeats).equals(metadata.duration())) {
            throw new IllegalArgumentException(
                    "Test FLAC files are a whole number of %s long".formatted(AUDIO_DURATION));
        }
        final var outputStream = new ByteArrayOutputStream();
        try {
            new FlacWriter().writeMetadata(metadata, outputStream);
            final var frames = silentFrames();
            for (var repeat = 0; repeat < repeats; repeat++) {
                outputStream.write(frames);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return outputStream.toByteArray();
    }

    private static byte[] silentFrames() throws IOException {
        try (final var channel = FlacAudioSample.SILENCE_10_SECONDS.channel()) {
            // Parsing leaves the channel at the first audio frame.
            new FlacParser().parse(channel);
            return Channels.newInputStream(channel).readAllBytes();
        } catch (InvalidFormatException e) {
            throw new IllegalStateException("The silence sample isn't valid FLAC", e);
        }
    }
}
