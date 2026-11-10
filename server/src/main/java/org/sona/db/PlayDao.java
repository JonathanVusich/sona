package org.sona.db;

import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.val;
import static org.sona.db.TrackFields.inLibrary;
import static org.sona.db.TrackFields.trackSummary;
import static org.sona.model.Tables.PLAY;
import static org.sona.model.Tables.TRACK;
import static org.sona.utils.IDGenerator.uuidv7;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.sona.mappers.PlayMapper;
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

    /**
     * Records that the user played the track just now.
     *
     * @return the play, if the track is in the library
     */
    public Optional<Play> insert(final UUID userId, final UUID trackId) {
        return dsl.insertInto(PLAY, PLAY.PLAY_ID, PLAY.USER_ID, PLAY.TRACK_ID)
                .select(select(val(uuidv7()), val(userId), TRACK.TRACK_ID)
                        .from(TRACK)
                        .where(TRACK.TRACK_ID.eq(trackId))
                        .and(inLibrary(TRACK)))
                .returning()
                .fetchOptional(playMapper::fromRecord);
    }

    /**
     * @return up to {@code size} of the user's latest plays, latest first
     */
    public List<PlayedTrack> loadFirstPage(final UUID userId, final int size) {
        return loadPage(PLAY.USER_ID.eq(userId), size);
    }

    /**
     * Play IDs are UUIDv7, so the plays after {@code after} in time are those with a smaller ID.
     *
     * @param after the last play of the page before
     * @return up to {@code size} of the user's plays that came before {@code after}, latest first
     */
    public List<PlayedTrack> loadNextPage(final UUID userId, final UUID after, final int size) {
        return loadPage(PLAY.USER_ID.eq(userId).and(PLAY.PLAY_ID.lt(after)), size);
    }

    private List<PlayedTrack> loadPage(final Condition plays, final int size) {
        return dsl.select(PLAY.PLAY_ID, trackSummary(PLAY.track()))
                .from(PLAY)
                .where(plays)
                .orderBy(PLAY.PLAY_ID.desc())
                .limit(size)
                .fetch(Records.mapping(PlayedTrack::new));
    }
}
