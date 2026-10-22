package org.sona.format;

import java.io.IOException;
import java.io.InputStream;
import org.sona.exception.InvalidFormatException;

public interface Parser {

    /**
     * Reads the file's metadata, leaving the stream at its audio for the result to read from. The caller still owns
     * the stream.
     */
    ParsedAudio parse(InputStream inputStream) throws IOException, InvalidFormatException;

    Format format();
}
