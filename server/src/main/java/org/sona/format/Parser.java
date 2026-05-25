package org.sona.format;

import org.sona.exception.InvalidFormatException;
import org.sona.metadata.RawMetadata;

import java.io.IOException;
import java.io.InputStream;

public interface Parser {

    RawMetadata parse(InputStream inputStream) throws IOException, InvalidFormatException;

    Format format();
}
