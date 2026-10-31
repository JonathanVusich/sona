package org.sona.engine;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sona.db.IngestDao;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Looks for pending ingests, once all the files of their group are uploaded, in the database once a second ({@code sona.ingest-worker.cron}), so uploads are processed
 * soon after they arrive, and those still pending when Sona stopped are processed when it starts again. Passes never
 * overlap: the next starts only once one has finished.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class DefaultIngestWorker implements IngestWorker {

    private final IngestionEngine engine;
    private final IngestDao ingestDao;
    private final IngestProgressPublisher progressPublisher;

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
        var next = ingestDao.loadOldestReadyPending().orElse(null);
        while (next != null) {
            processGroup(next.groupIngestId());
            next = ingestDao.loadOldestReadyPending().orElse(null);
        }
    }

    // An album is matched as a whole. Loose files are matched one by one.
    private void processGroup(final UUID groupIngestId) throws InterruptedException {
        final var group = ingestDao.loadGroupById(groupIngestId).orElseThrow();
        final var pending = ingestDao.loadPendingByGroup(groupIngestId);
        switch (group.kind()) {
            case ALBUM -> processAlbum(pending);
            case UNKNOWN -> {
                for (final var ingest : pending) {
                    processIngest(ingest);
                }
            }
        }
    }

    private void processAlbum(final List<TrackToIngest> pending) throws InterruptedException {
        try {
            engine.processAlbum(pending);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Ingesting an album of {} files failed", pending.size(), e);
            for (final var ingest : pending) {
                failIfPending(ingest);
            }
        }
    }

    private void processIngest(final TrackToIngest ingest) throws InterruptedException {
        try {
            engine.processTrack(ingest);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Ingesting {} ({}) failed", ingest.trackIngestId(), ingest.originalFileName(), e);
            failIfPending(ingest);
        }
    }

    // The engine marks the failures it expects. Any other leaves the ingest pending, so it would be taken again
    // straight away.
    private void failIfPending(final TrackToIngest ingest) {
        if (ingestDao.loadTrackIngestById(ingest.trackIngestId()).state() == IngestState.PENDING) {
            ingestDao.updateState(ingest.trackIngestId(), IngestState.FAILED);
            progressPublisher.publish(ingest, IngestState.FAILED);
        }
    }
}
