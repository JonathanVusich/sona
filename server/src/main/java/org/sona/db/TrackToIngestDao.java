package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.TrackToIngestMapper;
import org.sona.model.Tables;
import org.sona.model.Track;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.records.TrackToIngestRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public final class TrackToIngestDao {

    @Autowired
    private final DSLContext dsl;

    @Autowired
    private final TrackToIngestMapper mapper;

    public void store(List<TrackToIngest> tracks) {
        final var records = tracks.stream()
                .map(mapper::toRecord).toList();

        dsl.batchStore(records)
                .execute();
    }

    public Stream<TrackToIngest> loadByGroup(UUID ingestGroupId) {
        return dsl.selectFrom(Tables.TRACK_TO_INGEST)
                .where(Tables.TRACK_TO_INGEST.GROUP_INGEST_ID.eq(ingestGroupId))
                .fetchStream()
                .map(mapper::fromRecord);
    }

    public TrackToIngest load(UUID trackToIngestId) {
        final var record = dsl.selectFrom(Tables.TRACK_TO_INGEST)
                .where(Tables.TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .fetchOne();
        return mapper.fromRecord(record);
    }

    public Stream<TrackToIngest> pending() {
        return dsl.selectFrom(Tables.TRACK_TO_INGEST)
                .where(Tables.TRACK_TO_INGEST.STATE.eq(IngestState.PENDING))
                .fetchStream()
                .map(mapper::fromRecord);
    }
}
