package org.sona.engine;

/**
 * Processes pending ingests in the background, oldest first, one at a time.
 */
public interface IngestWorker {

    /**
     * Tells the worker there are ingests waiting, such as one just uploaded. Returns at once.
     */
    void notifyPending();
}
