package org.sona.db;

import static org.jooq.impl.DSL.selectOne;
import static org.sona.model.Tables.TRACK;
import static org.sona.model.Tables.TRACK_TO_INGEST;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.TrackMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Track;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class TrackDao {

    private final DSLContext dsl;
    private final TrackMapper mapper;

    public void insert(final Track track) {
        dsl.insertInto(TRACK).set(mapper.toRecord(track)).execute();
    }

    public Track loadByIngest(final UUID trackIngestId) {
        return dsl.selectFrom(TRACK)
                .where(TRACK.TRACK_INGEST_ID.eq(trackIngestId))
                .fetchSingle(mapper::fromRecord);
    }

    /**
     * @return the track, if it's in the library: a track is only once its ingest completes, as its row is stored
     * before its file is moved there
     */
    public Optional<Track> loadById(final UUID trackId) {
        return dsl.selectFrom(TRACK)
                .where(TRACK.TRACK_ID.eq(trackId))
                .andExists(selectOne()
                        .from(TRACK_TO_INGEST)
                        .where(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(TRACK.TRACK_INGEST_ID))
                        .and(TRACK_TO_INGEST.STATE.eq(IngestState.COMPLETED)))
                .fetchOptional(mapper::fromRecord);
    }
}
