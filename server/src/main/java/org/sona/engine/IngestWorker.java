package org.sona.engine;

/**
 * Processes pending ingests in the background, oldest first, one at a time.
 */
public interface IngestWorker {

    /**
     * Processes every pending ingest, and returns once none is left. Does nothing when there are none.
     */
    void processPending();
}
