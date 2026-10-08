package org.sona.samples;

import lombok.experimental.UtilityClass;
import org.sona.format.flac.FlacWriter;
import org.sona.metadata.RawMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Tiny synthetic FLAC files for tests, written by {@link FlacWriter}.
 */
@UtilityClass
public final class FlacFiles {

    /**
     * @return the metadata written as FLAC, followed by a few bytes of placeholder "audio" so file copies and moves can
     * be checked byte for byte
     */
    public static byte[] flacFile(final RawMetadata metadata) {
        final var outputStream = new ByteArrayOutputStream();
        try {
            new FlacWriter().write(metadata, outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (int i = 0; i < 256; i++) {
            outputStream.write(i);
        }
        return outputStream.toByteArray();
    }
}
