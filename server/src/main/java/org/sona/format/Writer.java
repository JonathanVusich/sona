package org.sona.format;

import org.sona.metadata.RawMetadata;

import java.io.IOException;
import java.io.OutputStream;

public interface Writer {

    OutputStream write(RawMetadata rawMetadata) throws IOException;

    Format format();
}
