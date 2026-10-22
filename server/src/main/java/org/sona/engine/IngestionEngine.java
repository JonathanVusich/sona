package org.sona.engine;

import java.io.IOException;
import org.sona.exception.InvalidFormatException;
import org.sona.model.tables.pojos.TrackToIngest;

/**
 * Ingest path:
 * Upload -> write to temp storage -> ingest async -> move to library
 */
public interface IngestionEngine {

    void processTrack(TrackToIngest trackToIngest) throws InvalidFormatException, IOException, InterruptedException;
}
