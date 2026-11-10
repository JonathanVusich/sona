package org.sona.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sona.db.IngestDao;
import org.sona.db.UploadedGroup;
import org.sona.exception.InvalidFormatException;
import org.sona.metadata.resolver.TrackResolution;
import org.sona.model.enums.IngestGroupState;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Looks for ingest groups whose files are all uploaded in the database once a second
 * ({@code sona.ingest-worker.cron}), so uploads are processed soon after they arrive, and those still waiting when Sona
 * stopped are processed when it starts again. Passes never overlap: the next starts only once one has finished.
 *
 * <p>The worker is the top of ingestion, as {@code ApiExceptionHandler} is of a request: the engine's steps throw what
 * fails, and the worker catches it once per file, records what it means in the file's state, and goes on with the
 * rest.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class DefaultIngestWorker implements IngestWorker {

    private final IngestionEngine engine;
    private final IngestDao ingestDao;

    @Scheduled(cron = "${sona.ingest-worker.cron}")
    @Override
    public void processPending() {
        try {
            processEachPending();
        } catch (RuntimeException e) {
            // Such as the database being out of reach. The ingests still pending are taken by the next pass.
            log.error("Processing pending ingests failed", e);
        }
    }

    private void processEachPending() {
        final var groups = ingestDao.loadUploadedGroups();
        for (final var uploaded : groups) {
            processGroup(uploaded);
            ingestDao.setGroupState(uploaded.group().ingestGroupId(), IngestGroupState.MATCHED);
        }
    }

    // An album is matched as a whole. Loose files are matched one by one.
    private void processGroup(final UploadedGroup uploaded) {
        final var pending = uploaded.trackIngests();
        switch (uploaded.group().kind()) {
            case ALBUM -> processAlbum(pending);
            case UNKNOWN -> {
                for (final var ingest : pending) {
                    processLooseFile(ingest);
                }
            }
        }
    }

    private void processLooseFile(final TrackToIngest ingest) {
        try {
            final var parsed = engine.parseTrack(ingest);
            importTrack(parsed, engine.resolveTrack(parsed));
        } catch (InvalidFormatException | IOException | RuntimeException e) {
            fail(ingest, e);
        }
    }

    private void processAlbum(final List<TrackToIngest> pending) {
        final var parsedTracks = parseAlbum(pending);
        final List<TrackResolution> resolutions;
        try {
            resolutions = engine.resolveAlbum(parsedTracks);
        } catch (RuntimeException e) {
            for (final var parsed : parsedTracks) {
                fail(parsed.trackToIngest(), e);
            }
            return;
        }
        for (var index = 0; index < parsedTracks.size(); index++) {
            importAlbumTrack(parsedTracks.get(index), resolutions.get(index));
        }
    }

    // A file that can't be read fails, and the rest of the album is still matched.
    private List<ParsedTrack> parseAlbum(final List<TrackToIngest> pending) {
        final var parsedTracks = new ArrayList<ParsedTrack>();
        for (final var ingest : pending) {
            try {
                parsedTracks.add(engine.parseTrack(ingest));
            } catch (InvalidFormatException | IOException e) {
                fail(ingest, e);
            }
        }
        return parsedTracks;
    }

    private void importAlbumTrack(final ParsedTrack parsed, final TrackResolution resolution) {
        try {
            importTrack(parsed, resolution);
        } catch (RuntimeException e) {
            fail(parsed.trackToIngest(), e);
        }
    }

    private void importTrack(final ParsedTrack parsed, final TrackResolution resolution) {
        final var ingest = parsed.trackToIngest();
        switch (resolution) {
            case TrackResolution.Matched matched -> moveTrack(ingest, engine.storeTrack(parsed, matched.track()));
            case TrackResolution.NeedsReview review -> engine.requireInput(ingest, review.candidates());
        }
    }

    // The track is stored, so only its file is missing from the library.
    private void moveTrack(final TrackToIngest ingest, final Track track) {
        try {
            engine.moveTrack(ingest, track);
        } catch (IOException e) {
            log.warn("Moving {} ({}) into the library failed", ingest.trackIngestId(), ingest.originalFileName(), e);
            engine.markState(ingest, IngestState.MOVE_FAILED);
        }
    }

    private void fail(final TrackToIngest ingest, final Exception e) {
        log.warn("Ingesting {} ({}) failed", ingest.trackIngestId(), ingest.originalFileName(), e);
        engine.markState(ingest, IngestState.FAILED);
    }
}
