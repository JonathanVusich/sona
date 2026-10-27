package org.sona.db;

import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.orderBy;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.unnest;
import static org.jooq.impl.DSL.val;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.PLAYLIST;
import static org.sona.model.Tables.PLAYLIST_TRACK;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_GROUP;
import static org.sona.model.Tables.TRACK;
import static org.sona.model.Tables.TRACK_TO_INGEST;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Records;
import org.sona.mappers.PlaylistMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Playlist;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users' playlists and the tracks on them. Each user sees only their own, so every method that finds a playlist takes
 * the user, and finds nothing for a playlist of someone else's. The reads convert every table they select to its pojo
 * by its table's mapper as it's read, so the composite mappers build their results from pojos only.
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

    public void insert(final Playlist playlist) {
        dsl.insertInto(PLAYLIST).set(playlistMapper.toRecord(playlist)).execute();
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
     * @param description null for none
     * @return the playlist as it is now, if the user has it
     */
    public Optional<Playlist> updateDetails(
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
        final var distinctTrackIds = trackIds.stream().distinct().toList();
        return lockPlaylist(userId, playlistId).map(_ -> appendTracks(playlistId, distinctTrackIds));
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
    public Optional<Integer> updateTrackPosition(
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
        final var trackId = firstTracks.field(PLAYLIST_TRACK.TRACK_ID);
        final var position = firstTracks.field(PLAYLIST_TRACK.POSITION);
        return multiset(select(RELEASE_GROUP.NAME)
                        .from(firstTracks)
                        .join(TRACK)
                        .on(TRACK.TRACK_ID.eq(trackId))
                        .join(MEDIUM)
                        .on(MEDIUM.MEDIUM_ID.eq(TRACK.MEDIUM_ID))
                        .join(RELEASE)
                        .on(RELEASE.RELEASE_ID.eq(MEDIUM.RELEASE_ID))
                        .join(RELEASE_GROUP)
                        .on(RELEASE_GROUP.RELEASE_GROUP_ID.eq(RELEASE.RELEASE_GROUP_ID))
                        .groupBy(RELEASE_GROUP.RELEASE_GROUP_ID, RELEASE_GROUP.NAME)
                        .orderBy(min(position))
                        .limit(COVER_SIZE))
                .convertFrom(names -> names.map(Record1::value1));
    }

    private Field<List<PlaylistEntry>> entries() {
        return multiset(select(
                                TRACK.TRACK_ID,
                                TRACK.NAME,
                                TRACK.DURATION,
                                TRACK.POSITION,
                                ARTIST.ARTIST_ID,
                                ARTIST.NAME,
                                RELEASE.RELEASE_ID,
                                RELEASE.NAME)
                        .from(PLAYLIST_TRACK)
                        .join(TRACK)
                        .on(TRACK.TRACK_ID.eq(PLAYLIST_TRACK.TRACK_ID))
                        .join(ARTIST)
                        .on(ARTIST.ARTIST_ID.eq(TRACK.ARTIST_ID))
                        .join(MEDIUM)
                        .on(MEDIUM.MEDIUM_ID.eq(TRACK.MEDIUM_ID))
                        .join(RELEASE)
                        .on(RELEASE.RELEASE_ID.eq(MEDIUM.RELEASE_ID))
                        .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(PLAYLIST.PLAYLIST_ID))
                        .orderBy(PLAYLIST_TRACK.POSITION))
                .convertFrom(entries -> entries.map(Records.mapping(PlaylistEntry::new)));
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

    private int appendTracks(final UUID playlistId, final List<UUID> trackIds) {
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

    private int removeTrack(final UUID playlistId, final UUID trackId) {
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
}
