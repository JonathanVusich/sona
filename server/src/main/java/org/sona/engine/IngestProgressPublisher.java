package org.sona.engine;

import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;

/**
 * Tells the clients watching ingests how each one is getting on.
 */
public interface IngestProgressPublisher {

    /**
     * Tells them the ingest's state is now {@code state}. Call it once the state is stored, so a client that then asks
     * for the unfinished ingests sees it too.
     */
    void publish(TrackToIngest ingest, IngestState state);
}
