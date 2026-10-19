package org.sona.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sona.db.TrackToIngestDao;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

/**
 * Takes pending ingests from the database, so those still pending when Sona stopped are processed when it starts
 * again. One thread is enough: MusicBrainz takes about one request a second, and Sona runs as a single instance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class DefaultIngestWorker implements IngestWorker, SmartLifecycle {

    private final IngestionEngine engine;
    private final TrackToIngestDao trackToIngestDao;

    // Each permit means there may be pending ingests. It starts with one, for those left from before a restart.
    private final Semaphore wakeUps = new Semaphore(1);
    private volatile Thread thread;

    @Override
    public void notifyPending() {
        wakeUps.release();
    }

    @Override
    public void start() {
        thread = Thread.ofPlatform().name("ingest-worker").start(this::processUntilStopped);
    }

    @Override
    public void stop() {
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return thread != null && thread.isAlive();
    }

    private void processUntilStopped() {
        try {
            while (true) {
                wakeUps.acquire();
                // One pass takes every ingest pending by then, however many uploads woke the worker.
                wakeUps.drainPermits();
                processPendingSafely();
            }
        } catch (InterruptedException _) {
            // Sona is stopping. An ingest it was processing is still pending, and is taken again on the next start.
        }
    }

    private void processPendingSafely() throws InterruptedException {
        try {
            processPending();
        } catch (RuntimeException e) {
            // Such as the database being out of reach. The thread must live on, or nothing would be ingested until Sona
            // restarted; the ingests still pending are taken on the next wake-up.
            log.error("Processing pending ingests failed", e);
        }
    }

    private void processPending() throws InterruptedException {
        var next = trackToIngestDao.loadOldestPending().orElse(null);
        while (next != null) {
            processIngest(next);
            next = trackToIngestDao.loadOldestPending().orElse(null);
        }
    }

    private void processIngest(final TrackToIngest ingest) throws InterruptedException {
        try {
            engine.processTrack(ingest);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Ingesting {} ({}) failed", ingest.trackIngestId(), ingest.originalFileName(), e);
            // The engine marks the failures it expects. Any other leaves the ingest pending, so it would be taken again
            // straight away.
            if (trackToIngestDao.loadById(ingest.trackIngestId()).state() == IngestState.PENDING) {
                trackToIngestDao.updateState(ingest.trackIngestId(), IngestState.FAILED);
            }
        }
    }
}
