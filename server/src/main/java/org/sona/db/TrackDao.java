package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.TrackMapper;
import org.sona.model.tables.pojos.Track;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.sona.model.Tables.TRACK;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TrackDao {

    private final DSLContext dsl;
    private final TrackMapper mapper;

    public void insert(Track track) {
        dsl.executeInsert(mapper.toRecord(track));
    }

    public Track loadByIngest(UUID trackIngestId) {
        return dsl.selectFrom(TRACK)
                .where(TRACK.TRACK_INGEST_ID.eq(trackIngestId))
                .fetchSingle(mapper::fromRecord);
    }
}
