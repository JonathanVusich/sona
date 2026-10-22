package org.sona.engine;

import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;

/**
 * Tells uploaders how the ingests of their files are getting on.
 */
public interface IngestProgressPublisher {

    /**
     * Tells the ingest's uploader that its state is now {@code state}. Call it once the state is stored.
     */
    void publish(TrackToIngest ingest, IngestState state);
}
