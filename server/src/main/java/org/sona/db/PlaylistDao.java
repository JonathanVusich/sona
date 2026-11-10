package org.sona.db;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.greatest;
import static org.jooq.impl.DSL.least;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.orderBy;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.unnest;
import static org.jooq.impl.DSL.val;
import static org.jooq.impl.DSL.when;
import static org.sona.db.TrackFields.inLibrary;
import static org.sona.db.TrackFields.trackSummary;
import static org.sona.model.Tables.PLAYLIST;
import static org.sona.model.Tables.PLAYLIST_TRACK;
import static org.sona.model.Tables.TRACK;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Records;
import org.sona.mappers.PlaylistMapper;
import org.sona.model.tables.pojos.Playlist;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users' playlists and the tracks on them. Each user sees only their own, so every method that finds a playlist takes
 * the user, and finds nothing for a playlist of someone else's. A playlist's row is locked for each change to its
 * tracks, so changes made at the same time take positions one after the other. Positions run from 1 without gaps, so
 * the playlist's track count is its last position, and they are unique only at commit, so the tracks a change shifts
 * can pass through each other's positions on the way.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PlaylistDao {

    private static final int COVER_SIZE = 4;
    // The cover's release groups are picked from the playlist's first tracks only, so a long playlist isn't read whole.
    private static final int COVER_TRACKS = 100;

    private final DSLContext dsl;
    private final PlaylistMapper playlistMapper;

    /**
     * Stores the playlist with the tracks on it, as {@link #insertTracks} adds them.
     */
    public void insert(final Playlist playlist, final List<UUID> trackIds) {
        dsl.insertInto(PLAYLIST).set(playlistMapper.toRecord(playlist)).execute();
        appendTracks(playlist.playlistId(), 0, trackIds);
    }

    /**
     * @return the user's playlists by name, each with what its cover shows
     */
    public List<PlaylistSummary> loadSummaries(final UUID userId) {
        return dsl.select(PLAYLIST.PLAYLIST_ID, PLAYLIST.NAME, PLAYLIST.TRACK_COUNT, coverNames())
                .from(PLAYLIST)
                .where(PLAYLIST.USER_ID.eq(userId))
                .orderBy(PLAYLIST.NAME, PLAYLIST.PLAYLIST_ID)
                .fetch(Records.mapping(PlaylistSummary::new));
    }

    /**
     * @return the playlist with every track on it in order, if the user has it
     */
    public Optional<PlaylistDetails> loadDetails(final UUID userId, final UUID playlistId) {
        return dsl.select(PLAYLIST.PLAYLIST_ID, PLAYLIST.NAME, PLAYLIST.DESCRIPTION, entries())
                .from(PLAYLIST)
                .where(PLAYLIST.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST.USER_ID.eq(userId))
                .fetchOptional(Records.mapping(PlaylistDetails::new));
    }

    /**
     * Sets the playlist's name and description.
     *
     * @param description null for none
     * @return the playlist as it is now, if the user has it
     */
    public Optional<Playlist> rename(
            final UUID userId, final UUID playlistId, final String name, final String description) {
        return dsl.update(PLAYLIST)
                .set(PLAYLIST.NAME, name)
                .set(PLAYLIST.DESCRIPTION, description)
                .where(PLAYLIST.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST.USER_ID.eq(userId))
                .returning()
                .fetchOptional(playlistMapper::fromRecord);
    }

    /**
     * Deletes the playlist with its list of tracks, which stay in the library.
     *
     * @return the playlist deleted, if the user had it
     */
    public Optional<Playlist> delete(final UUID userId, final UUID playlistId) {
        return dsl.deleteFrom(PLAYLIST)
                .where(PLAYLIST.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST.USER_ID.eq(userId))
                .returning()
                .fetchOptional(playlistMapper::fromRecord);
    }

    /**
     * Adds the tracks to the end of the playlist in the order given. Tracks already on it, given twice, or not in the
     * library are skipped.
     *
     * @return how many tracks were added, if the user has the playlist
     */
    public Optional<Integer> insertTracks(final UUID userId, final UUID playlistId, final List<UUID> trackIds) {
        return lockPlaylist(userId, playlistId).map(trackCount -> appendTracks(playlistId, trackCount, trackIds));
    }

    /**
     * Removes the track from the playlist, and moves the tracks after it up. A track that isn't on it is left out.
     *
     * @return how many tracks were removed, none or one, if the user has the playlist
     */
    public Optional<Integer> deleteTrack(final UUID userId, final UUID playlistId, final UUID trackId) {
        return lockPlaylist(userId, playlistId).map(_ -> removeTrack(playlistId, trackId));
    }

    /**
     * Moves the track to the position, and the tracks it passes along by one. A track that isn't on the playlist is
     * left out.
     *
     * @param position from 1; past the end puts the track last
     * @return how many tracks were moved, none or one, if the user has the playlist
     */
    public Optional<Integer> setTrackPosition(
            final UUID userId, final UUID playlistId, final UUID trackId, final int position) {
        return lockPlaylist(userId, playlistId)
                .map(trackCount -> moveTrack(playlistId, trackId, Math.min(position, trackCount)));
    }

    /**
     * @return the names of the release groups the playlist's first tracks are from, in the order they first appear
     */
    private static Field<List<String>> coverNames() {
        final var firstTracks = select(PLAYLIST_TRACK.TRACK_ID, PLAYLIST_TRACK.POSITION)
                .from(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(PLAYLIST.PLAYLIST_ID))
                .orderBy(PLAYLIST_TRACK.POSITION)
                .limit(COVER_TRACKS)
                .asTable("first_tracks");
        final var releaseGroup = TRACK.medium().release().releaseGroup();
        return multiset(select(releaseGroup.NAME)
                        .from(firstTracks)
                        .join(TRACK)
                        .on(TRACK.TRACK_ID.eq(firstTracks.field(PLAYLIST_TRACK.TRACK_ID)))
                        .groupBy(releaseGroup.RELEASE_GROUP_ID, releaseGroup.NAME)
                        .orderBy(min(firstTracks.field(PLAYLIST_TRACK.POSITION)))
                        .limit(COVER_SIZE))
                .convertFrom(names -> names.map(Record1::value1));
    }

    private static Field<List<TrackSummary>> entries() {
        return multiset(select(trackSummary(PLAYLIST_TRACK.track()))
                        .from(PLAYLIST_TRACK)
                        .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(PLAYLIST.PLAYLIST_ID))
                        .orderBy(PLAYLIST_TRACK.POSITION))
                .convertFrom(entries -> entries.map(Record1::value1));
    }

    /**
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

    private int appendTracks(final UUID playlistId, final int trackCount, final List<UUID> trackIds) {
        // The tracks given, each once, numbered in the order given.
        final var given = unnest(trackIds.stream().distinct().toArray(UUID[]::new))
                .withOrdinality()
                .as("given", "track_id", "given_order");
        final var alreadyOn = selectOne()
                .from(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST_TRACK.TRACK_ID.eq(TRACK.TRACK_ID));
        final var position = val(trackCount).plus(rowNumber().over(orderBy(given.field("given_order", Long.class))));
        return dsl.insertInto(
                        PLAYLIST_TRACK, PLAYLIST_TRACK.PLAYLIST_ID, PLAYLIST_TRACK.TRACK_ID, PLAYLIST_TRACK.POSITION)
                .select(select(val(playlistId), TRACK.TRACK_ID, position)
                        .from(given)
                        .join(TRACK)
                        .on(TRACK.TRACK_ID.eq(given.field("track_id", UUID.class)))
                        .where(inLibrary(TRACK))
                        .andNotExists(alreadyOn))
                .execute();
    }

    private int removeTrack(final UUID playlistId, final UUID trackId) {
        final var removed = dsl.deleteFrom(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST_TRACK.TRACK_ID.eq(trackId))
                .returning(PLAYLIST_TRACK.POSITION)
                .fetchOptional(PLAYLIST_TRACK.POSITION);
        removed.ifPresent(position -> dsl.update(PLAYLIST_TRACK)
                .set(PLAYLIST_TRACK.POSITION, PLAYLIST_TRACK.POSITION.minus(1))
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST_TRACK.POSITION.gt(position))
                .execute());
        return removed.isPresent() ? 1 : 0;
    }

    /**
     * Moves the track, and the tracks between its position and the target, in one statement: those it passes on the
     * way up move down one, and those it passes on the way down move up one.
     */
    private int moveTrack(final UUID playlistId, final UUID trackId, final int target) {
        final var position = PLAYLIST_TRACK.POSITION;
        final var current = field(select(position)
                .from(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST_TRACK.TRACK_ID.eq(trackId)));
        final var moved = dsl.update(PLAYLIST_TRACK)
                .set(
                        position,
                        when(PLAYLIST_TRACK.TRACK_ID.eq(trackId), val(target))
                                .when(position.lt(current), position.plus(1))
                                .otherwise(position.minus(1)))
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .and(position.between(least(current, val(target)), greatest(current, val(target))))
                .execute();
        return moved > 0 ? 1 : 0;
    }
}
