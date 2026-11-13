package org.sona.samples;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

/**
 * Ten seconds of silence that FFmpeg encoded as FLAC, the audio {@link FlacFiles} gives its files.
 */
public enum FlacAudioSample {
    SILENCE_10_SECONDS("silence_10s.flac");

    private final String fileName;

    FlacAudioSample(final String fileName) {
        this.fileName = fileName;
    }

    public InputStream inputStream() {
        return FlacAudioSample.class.getResourceAsStream("audio/" + fileName);
    }

    /**
     * @return the file in memory, as parsers read it
     */
    public SeekableByteChannel channel() throws IOException {
        try (final var inputStream = inputStream()) {
            return new SeekableInMemoryByteChannel(inputStream.readAllBytes());
        }
    }
}
