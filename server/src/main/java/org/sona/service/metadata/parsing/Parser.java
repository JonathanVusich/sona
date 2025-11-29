package org.sona.service.metadata.parsing;

import org.sona.format.Format;
import org.sona.format.TrackMetadata;
import org.sona.model.exception.InvalidFormatException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public interface Parser {

    TrackMetadata parse(InputStream inputStream) throws IOException, InvalidFormatException;

    Format format();
}
