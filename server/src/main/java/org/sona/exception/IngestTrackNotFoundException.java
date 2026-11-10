package org.sona.exception;

import java.util.UUID;

/**
 * An upload of a file no uploader created in an ingest group.
 */
public final class IngestTrackNotFoundException extends SonaException {

    public IngestTrackNotFoundException(final UUID trackIngestId) {
        super("There is no file %s to upload".formatted(trackIngestId));
    }
}
