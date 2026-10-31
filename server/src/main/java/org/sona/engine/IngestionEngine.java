package org.sona.engine;

import java.io.IOException;
import java.util.List;
import org.sona.exception.InvalidFormatException;
import org.sona.model.tables.pojos.TrackToIngest;

/**
 * Ingest path:
 * Upload -> write to temp storage -> ingest async -> move to library
 */
public interface IngestionEngine {

    void processTrack(TrackToIngest trackToIngest) throws InvalidFormatException, IOException, InterruptedException;

    /**
     * Matches the files of an album together, so they are imported as one release, and imports those it settles.
     * A file that isn't in a format Sona reads is marked failed, and doesn't stop the rest.
     */
    void processAlbum(List<TrackToIngest> tracks) throws IOException, InterruptedException;
}
