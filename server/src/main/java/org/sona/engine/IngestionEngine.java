package org.sona.engine;

import org.sona.model.exception.InvalidFormatException;
import org.sona.model.tables.pojos.TrackIngest;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Ingest path:
 * Upload -> write to temp storage -> ingest async -> move to library
 */
public interface IngestionEngine {

    void processTrack(TrackIngest trackToIngest) throws InvalidFormatException, IOException;
}
