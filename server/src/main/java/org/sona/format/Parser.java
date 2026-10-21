package org.sona.format;

import java.io.IOException;
import java.io.InputStream;
import org.sona.exception.InvalidFormatException;
import org.sona.metadata.RawMetadata;

public interface Parser {

    RawMetadata parseMetadata(InputStream inputStream) throws IOException, InvalidFormatException;

    Format format();
}
