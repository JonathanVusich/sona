package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.TrackMapper;
import org.sona.model.tables.pojos.Track;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jooq.impl.DSL.count;
import static org.sona.model.Tables.TRACK;

@Component
@RequiredArgsConstructor
@Transactional
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

    /**
     * @return the releases' tracks, release by release, each in disc and track order
     */
    public List<Track> loadByReleases(final Collection<UUID> releaseIds) {
        return dsl.selectFrom(TRACK)
                .where(TRACK.RELEASE_ID.in(releaseIds))
                .orderBy(TRACK.RELEASE_ID, TRACK.DISC_NUMBER, TRACK.TRACK_NUMBER)
                .fetch(mapper::fromRecord);
    }

    /**
     * @return how many tracks each release has, leaving out releases without any
     */
    public Map<UUID, Integer> countByReleases(final Collection<UUID> releaseIds) {
        return dsl.select(TRACK.RELEASE_ID, count())
                .from(TRACK)
                .where(TRACK.RELEASE_ID.in(releaseIds))
                .groupBy(TRACK.RELEASE_ID)
                .fetchMap(TRACK.RELEASE_ID, count());
    }
}
