package org.sona.engine;

/**
 * Processes pending ingests in the background, oldest first, one at a time.
 */
public interface IngestWorker {

    /**
     * Processes every pending ingest, such as one just uploaded, in the background. Returns at once.
     */
    void processPending();
}
