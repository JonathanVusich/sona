package org.sona.engine;

import org.sona.exception.InvalidFormatException;
import org.sona.model.tables.pojos.TrackToIngest;

import java.io.IOException;

/**
 * Ingest path:
 * Upload -> write to temp storage -> ingest async -> move to library
 */
public interface IngestionEngine {

    /**
     * @return whether a file of this name is in a format Sona can ingest, going by its extension
     */
    boolean supports(String filename);

    void processTrack(TrackToIngest trackToIngest) throws InvalidFormatException, IOException, InterruptedException;
}
