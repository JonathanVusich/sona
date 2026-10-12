package org.sona.format;

import org.sona.metadata.RawMetadata;

import java.io.IOException;
import java.io.OutputStream;

public interface Writer {

    void writeMetadata(RawMetadata rawMetadata, OutputStream outputStream) throws IOException;

    Format format();
}
