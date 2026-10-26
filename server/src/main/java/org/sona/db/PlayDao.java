package org.sona.db;

import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.val;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.PLAY;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.TRACK;
import static org.sona.model.Tables.TRACK_TO_INGEST;
import static org.sona.utils.IDGenerator.uuidv7;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.PlayMapper;
import org.sona.mappers.PlaylistEntryMapper;
import org.sona.mappers.ReleaseMapper;
import org.sona.mappers.TrackMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Play;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users' play history. Each user sees only their own, so every method takes the user.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PlayDao {

    private final DSLContext dsl;
    private final PlayMapper playMapper;
    private final TrackMapper trackMapper;
    private final ArtistMapper artistMapper;
    private final ReleaseMapper releaseMapper;
    private final PlaylistEntryMapper entryMapper;

    /**
     * Records that the user played the track just now.
     *
     * @return the play, if the track is in the library: a track is only once its ingest completes
     */
    public Optional<Play> insert(final UUID userId, final UUID trackId) {
        return dsl.insertInto(PLAY, PLAY.PLAY_ID, PLAY.USER_ID, PLAY.TRACK_ID)
                .select(select(val(uuidv7()), val(userId), TRACK.TRACK_ID)
                        .from(TRACK)
                        .join(TRACK_TO_INGEST)
                        .on(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(TRACK.TRACK_INGEST_ID))
                        .where(TRACK.TRACK_ID.eq(trackId))
                        .and(TRACK_TO_INGEST.STATE.eq(IngestState.COMPLETED)))
                .returning()
                .fetchOptional(playMapper::fromRecord);
    }

    /**
     * @return the tracks the user played last, latest first, each once for every time it was played, with its artist
     * and the release it's on
     */
    public List<PlaylistEntry> loadRecentTracks(final UUID userId, final int limit) {
        return dsl.select(
                        TRACK.convertFrom(trackMapper::fromRecord),
                        ARTIST.convertFrom(artistMapper::fromRecord),
                        RELEASE.convertFrom(releaseMapper::fromRecord))
                .from(PLAY)
                .join(TRACK)
                .on(TRACK.TRACK_ID.eq(PLAY.TRACK_ID))
                .join(ARTIST)
                .on(ARTIST.ARTIST_ID.eq(TRACK.ARTIST_ID))
                .join(MEDIUM)
                .on(MEDIUM.MEDIUM_ID.eq(TRACK.MEDIUM_ID))
                .join(RELEASE)
                .on(RELEASE.RELEASE_ID.eq(MEDIUM.RELEASE_ID))
                .where(PLAY.USER_ID.eq(userId))
                .orderBy(PLAY.PLAY_ID.desc())
                .limit(limit)
                .fetch(entry -> entryMapper.fromPojos(entry.value1(), entry.value2(), entry.value3()));
    }
}
