package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.TrackToIngestMapper;
import org.sona.model.Tables;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TrackToIngestDao {

    private final DSLContext dsl;
    private final TrackToIngestMapper mapper;

    public void store(List<TrackToIngest> tracks) {
        final var records = tracks.stream()
                .map(mapper::toRecord).toList();

        dsl.batchStore(records)
                .execute();
    }

    public List<TrackToIngest> loadByGroup(UUID ingestGroupId) {
        return dsl.selectFrom(Tables.TRACK_TO_INGEST)
                .where(Tables.TRACK_TO_INGEST.GROUP_INGEST_ID.eq(ingestGroupId))
                .fetch(mapper::fromRecord);
    }

    public TrackToIngest load(UUID trackToIngestId) {
        final var record = dsl.selectFrom(Tables.TRACK_TO_INGEST)
                .where(Tables.TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .fetchOne();
        return mapper.fromRecord(record);
    }

    public List<TrackToIngest> pending() {
        return dsl.selectFrom(Tables.TRACK_TO_INGEST)
                .where(Tables.TRACK_TO_INGEST.STATE.eq(IngestState.PENDING))
                .fetch(mapper::fromRecord);
    }

    public void updateState(UUID trackToIngestId, IngestState state) {
        dsl.update(Tables.TRACK_TO_INGEST)
                .set(Tables.TRACK_TO_INGEST.STATE, state)
                .where(Tables.TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .execute();
    }
}
