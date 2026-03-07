package org.sona.engine;

import org.sona.exception.InvalidFormatException;
import org.sona.model.tables.pojos.TrackIngest;

import java.io.IOException;

/**
 * Ingest path:
 * Upload -> write to temp storage -> ingest async -> move to library
 */
public interface IngestionEngine {

    void processTrack(TrackIngest trackToIngest) throws InvalidFormatException, IOException;
}
