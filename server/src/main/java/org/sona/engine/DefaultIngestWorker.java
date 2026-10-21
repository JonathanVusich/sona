package org.sona.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sona.db.TrackToIngestDao;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Looks for pending ingests in the database once a second ({@code sona.ingest-worker.cron}), so uploads are processed
 * soon after they arrive, and those still pending when Sona stopped are processed when it starts again. Passes never
 * overlap: the next starts only once one has finished.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class DefaultIngestWorker implements IngestWorker {

    private final IngestionEngine engine;
    private final TrackToIngestDao trackToIngestDao;

    @Scheduled(cron = "${sona.ingest-worker.cron}")
    @Override
    public void processPending() {
        try {
            processEachPending();
        } catch (InterruptedException _) {
            // Sona is stopping. An ingest it was processing is still pending, and is taken again on the next start.
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // Such as the database being out of reach. The ingests still pending are taken by the next pass.
            log.error("Processing pending ingests failed", e);
        }
    }

    private void processEachPending() throws InterruptedException {
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
