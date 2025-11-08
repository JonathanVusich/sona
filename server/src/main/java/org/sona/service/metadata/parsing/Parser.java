package org.sona.service.metadata.parsing;

import org.sona.format.flac.FileMetadata;
import org.sona.model.exception.InvalidFormatException;

import java.io.IOException;
import java.io.InputStream;

public interface Parser {

    FileMetadata parse(InputStream inputStream) throws IOException, InvalidFormatException;
}
