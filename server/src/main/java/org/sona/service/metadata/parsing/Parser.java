package org.sona.service.metadata.parsing;

import org.sona.format.Format;
import org.sona.metadata.RawMetadata;
import org.sona.exception.InvalidFormatException;

import java.io.IOException;
import java.io.InputStream;

public interface Parser {

    RawMetadata parse(InputStream inputStream) throws IOException, InvalidFormatException;

    Format format();
}
