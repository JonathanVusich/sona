package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.model.Tables;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.records.TrackToIngestRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public final class TrackToIngestDao {

    @Autowired
    private final DSLContext dsl;

    public void store(List<TrackToIngestRecord> tracks) {
        dsl.batchStore(tracks)
                .execute();
    }

    public Stream<TrackToIngestRecord> pending() {
        return dsl.selectFrom(Tables.TRACK_TO_INGEST)
                .where(Tables.TRACK_TO_INGEST.STATE.eq(IngestState.PENDING))
                .fetchStream();
    }
}
