package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.TrackMapper;
import org.sona.model.tables.pojos.Track;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class TrackDao {

    private final DSLContext dsl;
    private final TrackMapper mapper;

    public void insert(Track track) {
        dsl.executeInsert(mapper.toRecord(track));
    }
}
