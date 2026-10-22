package org.sona.format;

import java.io.IOException;
import java.io.OutputStream;
import org.sona.metadata.RawMetadata;

public interface Writer {

    void writeMetadata(RawMetadata rawMetadata, OutputStream outputStream) throws IOException;

    Format format();
}
