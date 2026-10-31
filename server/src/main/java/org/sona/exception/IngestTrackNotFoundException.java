package org.sona.exception;

import java.util.UUID;

/**
 * An upload of a file the uploader hasn't created in that ingest group.
 */
public final class IngestTrackNotFoundException extends SonaException {

    public IngestTrackNotFoundException(final UUID ingestGroupId, final UUID trackIngestId) {
        super("Ingest group %s has no file %s".formatted(ingestGroupId, trackIngestId));
    }
}
