package org.sona.store;

import lombok.RequiredArgsConstructor;
import org.sona.db.TrackToIngestDao;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Stores the tracks waiting to be ingested and how far each has got.
 */
@Service
@RequiredArgsConstructor
public class IngestStore {

    private final TrackToIngestDao trackToIngestDao;

    @Transactional
    public void store(final TrackToIngest trackToIngest) {
        trackToIngestDao.store(List.of(trackToIngest));
    }

    @Transactional
    public void markState(final UUID trackIngestId, final IngestState state) {
        trackToIngestDao.updateState(trackIngestId, state);
    }
}
