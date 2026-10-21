package org.sona.engine;

import org.sona.exception.InvalidFormatException;
import org.sona.model.tables.pojos.TrackToIngest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Ingest path:
 * Upload -> write to temp storage -> ingest async -> move to library
 */
public interface IngestionEngine {

    /**
     * Stores an uploaded file in the ingest area, to be processed later.
     *
     * @param filename the file's name where it was uploaded from, whose extension says its format
     * @return the stored ingest, waiting to be processed
     * @throws InvalidFormatException if Sona has no parser for the file's format, in which case nothing is stored
     */
    TrackToIngest storeUpload(UUID groupIngestId, String filename, InputStream file)
            throws InvalidFormatException, IOException;

    void processTrack(TrackToIngest trackToIngest) throws InvalidFormatException, IOException, InterruptedException;
}
