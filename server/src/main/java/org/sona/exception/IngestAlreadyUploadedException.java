package org.sona.exception;

import java.util.UUID;

/**
 * An upload of a file that has already been uploaded, and so may be matched or imported already.
 */
public final class IngestAlreadyUploadedException extends SonaException {

    public IngestAlreadyUploadedException(final UUID trackIngestId) {
        super("File %s has already been uploaded".formatted(trackIngestId));
    }
}
