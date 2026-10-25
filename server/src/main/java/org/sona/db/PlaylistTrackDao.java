package org.sona.db;

import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.orderBy;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.unnest;
import static org.jooq.impl.DSL.val;
import static org.sona.model.Tables.PLAYLIST;
import static org.sona.model.Tables.PLAYLIST_TRACK;
import static org.sona.model.Tables.TRACK;
import static org.sona.model.Tables.TRACK_TO_INGEST;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.sona.model.enums.IngestState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tracks on users' playlists. Like {@link PlaylistDao}, every method takes the user, and finds nothing for a
 * playlist of someone else's.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PlaylistTrackDao {

    private final DSLContext dsl;

    /**
     * Adds the tracks to the end of the playlist in the order given. Tracks already on it, given twice, or not in the
     * library are skipped.
     *
     * @return how many tracks were added, if the user has the playlist
     */
    public Optional<Integer> insertAll(final UUID userId, final UUID playlistId, final List<UUID> trackIds) {
        final var distinctTrackIds = trackIds.stream().distinct().toList();
        return lockPlaylist(userId, playlistId).map(_ -> insertAfterLast(playlistId, distinctTrackIds));
    }

    /**
     * Removes the track from the playlist, and moves the tracks after it up. A track that isn't on it is left out.
     *
     * @return how many tracks were removed, none or one, if the user has the playlist
     */
    public Optional<Integer> delete(final UUID userId, final UUID playlistId, final UUID trackId) {
        return lockPlaylist(userId, playlistId).map(_ -> deleteTrack(playlistId, trackId));
    }

    /**
     * Moves the track to the position, and the tracks it passes along by one. A track that isn't on the playlist is
     * left out.
     *
     * @param position from 1; past the end puts the track last
     * @return how many tracks were moved, none or one, if the user has the playlist
     */
    public Optional<Integer> updatePosition(
            final UUID userId, final UUID playlistId, final UUID trackId, final int position) {
        return lockPlaylist(userId, playlistId)
                .map(trackCount -> moveTrack(playlistId, trackId, Math.min(position, trackCount)));
    }

    /**
     * Locked, so changes to one playlist made at the same time take positions one after the other.
     *
     * @return how many tracks the playlist has, if the user has it
     */
    private Optional<Integer> lockPlaylist(final UUID userId, final UUID playlistId) {
        return dsl.select(PLAYLIST.TRACK_COUNT)
                .from(PLAYLIST)
                .where(PLAYLIST.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST.USER_ID.eq(userId))
                .forUpdate()
                .fetchOptional(PLAYLIST.TRACK_COUNT);
    }

    private int deleteTrack(final UUID playlistId, final UUID trackId) {
        final var removedPosition = dsl.deleteFrom(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST_TRACK.TRACK_ID.eq(trackId))
                .returning(PLAYLIST_TRACK.POSITION)
                .fetchOptional(PLAYLIST_TRACK.POSITION)
                .orElse(null);
        if (removedPosition == null) {
            return 0;
        }
        shiftPositions(playlistId, PLAYLIST_TRACK.POSITION.gt(removedPosition), -1);
        return 1;
    }

    private int moveTrack(final UUID playlistId, final UUID trackId, final int target) {
        final var current = dsl.select(PLAYLIST_TRACK.POSITION)
                .from(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST_TRACK.TRACK_ID.eq(trackId))
                .fetchOptional(PLAYLIST_TRACK.POSITION)
                .orElse(null);
        if (current == null) {
            return 0;
        }
        if (target < current) {
            shiftPositions(playlistId, PLAYLIST_TRACK.POSITION.between(target, current - 1), 1);
        } else if (target > current) {
            shiftPositions(playlistId, PLAYLIST_TRACK.POSITION.between(current + 1, target), -1);
        }
        dsl.update(PLAYLIST_TRACK)
                .set(PLAYLIST_TRACK.POSITION, target)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST_TRACK.TRACK_ID.eq(trackId))
                .execute();
        return 1;
    }

    // Positions are unique only at commit, so tracks can pass through each other's positions on the way.
    private void shiftPositions(final UUID playlistId, final Condition positions, final int offset) {
        dsl.update(PLAYLIST_TRACK)
                .set(PLAYLIST_TRACK.POSITION, PLAYLIST_TRACK.POSITION.plus(offset))
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .and(positions)
                .execute();
    }

    private int insertAfterLast(final UUID playlistId, final List<UUID> trackIds) {
        if (trackIds.isEmpty()) {
            return 0;
        }
        final var lastPosition = dsl.select(max(PLAYLIST_TRACK.POSITION))
                .from(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .fetchOptional(0, Integer.class)
                .orElse(0);
        // The tracks given, numbered in the order given.
        final var given = unnest(trackIds.toArray(UUID[]::new)).withOrdinality().as("given", "track_id", "given_order");
        final var givenTrackId = given.field("track_id", UUID.class);
        final var givenOrder = given.field("given_order", Long.class);
        final var alreadyOn = selectOne()
                .from(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST_TRACK.TRACK_ID.eq(TRACK.TRACK_ID));
        final var position = val(lastPosition).plus(rowNumber().over(orderBy(givenOrder)));
        return dsl.insertInto(
                        PLAYLIST_TRACK, PLAYLIST_TRACK.PLAYLIST_ID, PLAYLIST_TRACK.TRACK_ID, PLAYLIST_TRACK.POSITION)
                .select(select(val(playlistId), TRACK.TRACK_ID, position)
                        .from(given)
                        .join(TRACK)
                        .on(TRACK.TRACK_ID.eq(givenTrackId))
                        .join(TRACK_TO_INGEST)
                        .on(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(TRACK.TRACK_INGEST_ID))
                        .where(TRACK_TO_INGEST.STATE.eq(IngestState.COMPLETED))
                        .andNotExists(alreadyOn))
                .execute();
    }
}
