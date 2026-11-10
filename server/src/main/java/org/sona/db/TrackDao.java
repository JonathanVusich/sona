package org.sona.db;

import static org.sona.db.TrackFields.inLibrary;
import static org.sona.model.Tables.TRACK;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.TrackMapper;
import org.sona.model.tables.pojos.Track;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class TrackDao {

    private final DSLContext dsl;
    private final TrackMapper trackMapper;

    public void insert(final Track track) {
        dsl.insertInto(TRACK).set(trackMapper.toRecord(track)).execute();
    }

    /**
     * @return the track, if it's in the library
     */
    public Optional<Track> loadById(final UUID trackId) {
        return dsl.selectFrom(TRACK)
                .where(TRACK.TRACK_ID.eq(trackId))
                .and(inLibrary(TRACK))
                .fetchOptional(trackMapper::fromRecord);
    }
}
