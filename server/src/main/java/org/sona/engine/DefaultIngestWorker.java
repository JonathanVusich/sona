package org.sona.engine;

import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sona.db.IngestDao;
import org.sona.db.UploadedGroup;
import org.sona.exception.InvalidFormatException;
import org.sona.model.enums.IngestGroupState;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Looks for ingest groups whose files are all uploaded in the database once a second
 * ({@code sona.ingest-worker.cron}), so uploads are processed soon after they arrive, and those still waiting when Sona
 * stopped are processed when it starts again. Passes never overlap: the next starts only once one has finished.
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
        final var groups = ingestDao.loadUploadedGroups();
        for (final var uploaded : groups) {
            processGroup(uploaded);
            ingestDao.updateGroupState(uploaded.group().ingestGroupId(), IngestGroupState.MATCHED);
        }
    }

    // An album is matched as a whole. Loose files are matched one by one.
    private void processGroup(final UploadedGroup uploaded) throws InterruptedException {
        final var pending = uploaded.trackIngests();
        switch (uploaded.group().kind()) {
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
        } catch (IOException | RuntimeException e) {
            log.warn("Ingesting an album of {} files failed", pending.size(), e);
            for (final var ingest : pending) {
                failIfUploaded(ingest);
            }
        }
    }

    private void processIngest(final TrackToIngest ingest) throws InterruptedException {
        try {
            engine.processTrack(ingest);
        } catch (InvalidFormatException | IOException | RuntimeException e) {
            log.warn("Ingesting {} ({}) failed", ingest.trackIngestId(), ingest.originalFileName(), e);
            failIfUploaded(ingest);
        }
    }

    // The engine marks the failures it expects. Any other leaves the ingest uploaded, as if it had never been tried.
    private void failIfUploaded(final TrackToIngest ingest) {
        if (ingestDao.updateState(ingest.trackIngestId(), IngestState.UPLOADED, IngestState.FAILED)) {
            progressPublisher.publish(ingest, IngestState.FAILED);
        }
    }
}
