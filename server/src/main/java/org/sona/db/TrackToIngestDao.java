package org.sona.db;

import static org.sona.model.Tables.TRACK_TO_INGEST;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.TrackToIngestMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class TrackToIngestDao {

    private final DSLContext dsl;
    private final TrackToIngestMapper mapper;

    public void insert(final TrackToIngest trackToIngest) {
        dsl.insertInto(TRACK_TO_INGEST).set(mapper.toRecord(trackToIngest)).execute();
    }

    public TrackToIngest loadById(final UUID trackToIngestId) {
        return dsl.selectFrom(TRACK_TO_INGEST)
                .where(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .fetchSingle(mapper::fromRecord);
    }

    /**
     * @return the oldest ingest still waiting to be processed
     */
    public Optional<TrackToIngest> loadOldestPending() {
        return dsl.selectFrom(TRACK_TO_INGEST)
                .where(TRACK_TO_INGEST.STATE.eq(IngestState.PENDING))
                .orderBy(TRACK_TO_INGEST.TRACK_INGEST_ID)
                .limit(1)
                .fetchOptional(mapper::fromRecord);
    }

    /**
     * @return every ingest that hasn't been imported, oldest first
     */
    public List<TrackToIngest> loadUnfinished() {
        // Listing the states, rather than "not COMPLETED", lets the state index serve it.
        final var unfinished = Arrays.stream(IngestState.values())
                .filter(state -> state != IngestState.COMPLETED)
                .toList();
        return dsl.selectFrom(TRACK_TO_INGEST)
                .where(TRACK_TO_INGEST.STATE.in(unfinished))
                .orderBy(TRACK_TO_INGEST.TRACK_INGEST_ID)
                .fetch(mapper::fromRecord);
    }

    public void updateState(final UUID trackToIngestId, final IngestState state) {
        dsl.update(TRACK_TO_INGEST)
                .set(TRACK_TO_INGEST.STATE, state)
                .where(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .execute();
    }
}
