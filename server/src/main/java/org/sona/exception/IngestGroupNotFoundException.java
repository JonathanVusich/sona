package org.sona.exception;

import java.util.UUID;

/**
 * An upload into an ingest group the uploader hasn't created.
 */
public final class IngestGroupNotFoundException extends SonaException {

    public IngestGroupNotFoundException(final UUID ingestGroupId) {
        super("Ingest group %s doesn't exist".formatted(ingestGroupId));
    }
}
