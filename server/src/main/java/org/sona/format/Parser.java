package org.sona.format;

import org.sona.exception.InvalidFormatException;

import java.io.IOException;
import java.io.InputStream;

public interface Parser {

    ParsedAudio parse(InputStream inputStream) throws IOException, InvalidFormatException;

    Format format();
}
