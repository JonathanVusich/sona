package org.sona.db;

import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.orderBy;
import static org.jooq.impl.DSL.rand;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.unnest;
import static org.jooq.impl.DSL.val;
import static org.jooq.impl.DSL.when;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.PLAYLIST;
import static org.sona.model.Tables.PLAYLIST_TRACK;
import static org.sona.model.Tables.QUEUE;
import static org.sona.model.Tables.QUEUE_TRACK;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.TRACK;
import static org.sona.model.Tables.TRACK_TO_INGEST;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record10;
import org.jooq.Record2;
import org.jooq.Records;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.Table;
import org.sona.exception.PlaylistNotFoundException;
import org.sona.exception.QueueStartOutOfRangeException;
import org.sona.exception.ReleaseNotFoundException;
import org.sona.exception.TrackNotFoundException;
import org.sona.mappers.QueueMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;
import org.sona.model.tables.pojos.Queue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users' play queues. Each user has one, which only they see, so every method takes the user. A queue's row is created
 * the first time its user changes it. Every change locks the row and raises its version, so changes made at the same
 * time happen one after the other. A queue may be as long as the playlist it was made from, so its entries are read a
 * page at a time and written by statements that copy them from their source, never through the application.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class QueueDao {

    /**
     * Added to the play order of the shuffled entries that wrap round to before the anchor, so that a play order grows
     * along the whole queue however it is shuffled.
     */
    private static final int WRAPPED_ORDER = 1 << 30;

    /**
     * The play order is the second column of an entry's select.
     */
    private static final int PLAY_ORDER_COLUMN = 2;

    private final DSLContext dsl;
    private final QueueMapper queueMapper;

    /**
     * @return the user's queue without its entries, but with the current one, an empty queue if they have never
     * changed theirs
     */
    public QueueDetails loadDetails(final UUID userId) {
        return dsl.select(QUEUE.VERSION, currentEntry(), QUEUE.POSITION_SECONDS, QUEUE.SHUFFLE, QUEUE.REPEAT)
                .from(QUEUE)
                .where(QUEUE.USER_ID.eq(userId))
                .fetchOptional(Records.mapping(QueueDetails::new))
                .orElseGet(QueueDetails::empty);
    }

    /**
     * The queue plays in the order it was queued in, or when it is shuffled from its anchor to its last entry in
     * shuffle order and then from its first to the entry before the anchor. A statement reads whichever applies: the
     * other branches find nothing, and each branch is a range of an index.
     *
     * @param playOrder the play order of the entry before the page, 0 for the first page
     * @param size      how many entries at most
     * @return the entries that play after it, in play order
     */
    public List<QueueEntry> loadEntriesAfter(final UUID userId, final int playOrder, final int size) {
        final var anchor = loadShuffleAnchor(userId);
        final var queued = selectPageEntries(userId, ShuffleMode.OFF, QUEUE_TRACK.POSITION)
                .and(QUEUE_TRACK.POSITION.gt(playOrder))
                .orderBy(QUEUE_TRACK.POSITION)
                .limit(size);
        final var shuffled = selectPageEntries(userId, ShuffleMode.ON, QUEUE_TRACK.SHUFFLE_ORDER)
                .and(QUEUE_TRACK.SHUFFLE_ORDER.ge(anchor))
                .and(QUEUE_TRACK.SHUFFLE_ORDER.gt(playOrder))
                .orderBy(QUEUE_TRACK.SHUFFLE_ORDER)
                .limit(size);
        final var wrapped = selectPageEntries(userId, ShuffleMode.ON, QUEUE_TRACK.SHUFFLE_ORDER.plus(WRAPPED_ORDER))
                .and(QUEUE_TRACK.SHUFFLE_ORDER.lt(anchor))
                .and(QUEUE_TRACK.SHUFFLE_ORDER.gt(playOrder - WRAPPED_ORDER))
                .orderBy(QUEUE_TRACK.SHUFFLE_ORDER)
                .limit(size);
        return queued.unionAll(shuffled)
                .unionAll(wrapped)
                .orderBy(inline(PLAY_ORDER_COLUMN))
                .limit(size)
                .fetch(Records.mapping(QueueEntry::new));
    }

    /**
     * @param playOrder the play order of the entry after the page
     * @param size      how many entries at most
     * @return the entries that play before it, the nearest first
     */
    public List<QueueEntry> loadEntriesBefore(final UUID userId, final int playOrder, final int size) {
        final var anchor = loadShuffleAnchor(userId);
        final var queued = selectPageEntries(userId, ShuffleMode.OFF, QUEUE_TRACK.POSITION)
                .and(QUEUE_TRACK.POSITION.lt(playOrder))
                .orderBy(QUEUE_TRACK.POSITION.desc())
                .limit(size);
        final var shuffled = selectPageEntries(userId, ShuffleMode.ON, QUEUE_TRACK.SHUFFLE_ORDER)
                .and(QUEUE_TRACK.SHUFFLE_ORDER.ge(anchor))
                .and(QUEUE_TRACK.SHUFFLE_ORDER.lt(playOrder))
                .orderBy(QUEUE_TRACK.SHUFFLE_ORDER.desc())
                .limit(size);
        final var wrapped = selectPageEntries(userId, ShuffleMode.ON, QUEUE_TRACK.SHUFFLE_ORDER.plus(WRAPPED_ORDER))
                .and(QUEUE_TRACK.SHUFFLE_ORDER.lt(anchor))
                .and(QUEUE_TRACK.SHUFFLE_ORDER.lt(playOrder - WRAPPED_ORDER))
                .orderBy(QUEUE_TRACK.SHUFFLE_ORDER.desc())
                .limit(size);
        return queued.unionAll(shuffled)
                .unionAll(wrapped)
                .orderBy(inline(PLAY_ORDER_COLUMN).desc())
                .limit(size)
                .fetch(Records.mapping(QueueEntry::new));
    }

    /**
     * Empties the queue and fills it with the tracks, a new entry for each, and makes the one at {@code startIndex}
     * the current one, at the start. If the queue is shuffled, the entries after the current one play in a random
     * order.
     *
     * @param startIndex which of the tracks plays first, from 0
     * @return the queue as it is now
     * @throws TrackNotFoundException        if a track isn't in the library
     * @throws QueueStartOutOfRangeException if there is no track at {@code startIndex}
     */
    public QueueDetails replaceWithTracks(final UUID userId, final List<UUID> trackIds, final int startIndex) {
        requireInLibrary(trackIds);
        // The tracks given, numbered in the order given.
        final var given = unnest(trackIds.toArray(UUID[]::new)).withOrdinality().as("given", "track_id", "given_order");
        final var tracks = select(
                        given.field("track_id", UUID.class),
                        given.field("given_order", Long.class).cast(Integer.class))
                .from(given);
        return replaceFrom(userId, tracks, startIndex);
    }

    /**
     * Fills the queue with the playlist's tracks, as {@link #replaceWithTracks} does.
     *
     * @throws PlaylistNotFoundException if the user doesn't have the playlist
     */
    public QueueDetails replaceWithPlaylist(final UUID userId, final UUID playlistId, final int startIndex) {
        final var owned = PLAYLIST.PLAYLIST_ID.eq(playlistId).and(PLAYLIST.USER_ID.eq(userId));
        if (!dsl.fetchExists(PLAYLIST, owned)) {
            throw new PlaylistNotFoundException(playlistId);
        }
        final var tracks = select(PLAYLIST_TRACK.TRACK_ID, PLAYLIST_TRACK.POSITION)
                .from(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId));
        return replaceFrom(userId, tracks, startIndex);
    }

    /**
     * Fills the queue with the release's tracks in the order of its media, as {@link #replaceWithTracks} does.
     *
     * @throws ReleaseNotFoundException if the release has no track in the library
     */
    public QueueDetails replaceWithRelease(final UUID userId, final UUID releaseId, final int startIndex) {
        final var inLibrary = RELEASE.RELEASE_ID.eq(releaseId).and(RELEASE.TRACK_COUNT.gt(0));
        if (!dsl.fetchExists(RELEASE, inLibrary)) {
            throw new ReleaseNotFoundException(releaseId);
        }
        final var tracks = select(TRACK.TRACK_ID, rowNumber().over(orderBy(MEDIUM.POSITION, TRACK.POSITION)))
                .from(TRACK)
                .join(MEDIUM)
                .on(MEDIUM.MEDIUM_ID.eq(TRACK.MEDIUM_ID))
                .join(TRACK_TO_INGEST)
                .on(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(TRACK.TRACK_INGEST_ID))
                .where(MEDIUM.RELEASE_ID.eq(releaseId))
                .and(TRACK_TO_INGEST.STATE.eq(IngestState.COMPLETED));
        return replaceFrom(userId, tracks, startIndex);
    }

    /**
     * Moves the player to the entry, at its start.
     *
     * @return the queue's new version, if the entry is in the user's queue
     */
    public Optional<Long> updateCurrentEntry(final UUID userId, final UUID entryId) {
        return dsl.update(QUEUE)
                .set(QUEUE.CURRENT_ENTRY_ID, entryId)
                .set(QUEUE.POSITION_SECONDS, 0)
                .set(QUEUE.VERSION, QUEUE.VERSION.plus(1))
                .where(QUEUE.USER_ID.eq(userId))
                .andExists(selectOne()
                        .from(QUEUE_TRACK)
                        .where(QUEUE_TRACK.QUEUE_TRACK_ID.eq(entryId))
                        .and(QUEUE_TRACK.USER_ID.eq(userId)))
                .returning(QUEUE.VERSION)
                .fetchOptional(QUEUE.VERSION);
    }

    /**
     * Records how far into the entry the player is. A report for an entry that isn't the current one is out of date,
     * as the player has moved on since, and is left out.
     */
    public void updatePositionSeconds(final UUID userId, final UUID entryId, final int seconds) {
        dsl.update(QUEUE)
                .set(QUEUE.POSITION_SECONDS, seconds)
                .set(QUEUE.VERSION, QUEUE.VERSION.plus(1))
                .where(QUEUE.USER_ID.eq(userId))
                .and(QUEUE.CURRENT_ENTRY_ID.eq(entryId))
                .execute();
    }

    /**
     * Turning shuffle on starts the shuffled order from the current entry, and turning it off goes back to the order
     * the entries were queued in. Both orders are kept with the entries, so no entry changes.
     */
    public void updateModes(final UUID userId, final ShuffleMode shuffle, final RepeatMode repeat) {
        lockQueue(userId);
        dsl.update(QUEUE)
                .set(QUEUE.SHUFFLE, shuffle)
                .set(QUEUE.REPEAT, repeat)
                .set(QUEUE.SHUFFLE_ANCHOR, shuffleAnchor(shuffle))
                .set(QUEUE.VERSION, QUEUE.VERSION.plus(1))
                .where(QUEUE.USER_ID.eq(userId))
                .execute();
    }

    /**
     * Empties the queue. Its shuffle and repeat modes stay as they are.
     */
    public void delete(final UUID userId) {
        dsl.update(QUEUE)
                .setNull(QUEUE.CURRENT_ENTRY_ID)
                .set(QUEUE.POSITION_SECONDS, 0)
                .set(QUEUE.SHUFFLE_ANCHOR, 1)
                .set(QUEUE.VERSION, QUEUE.VERSION.plus(1))
                .where(QUEUE.USER_ID.eq(userId))
                .execute();
        dsl.deleteFrom(QUEUE_TRACK).where(QUEUE_TRACK.USER_ID.eq(userId)).execute();
    }

    /**
     * The entries' columns and joins, for the caller to filter and order.
     *
     * @param playOrder where each entry plays
     */
    private SelectOnConditionStep<Record10<UUID, Integer, UUID, String, Integer, Integer, UUID, String, UUID, String>>
            selectEntries(final Field<Integer> playOrder) {
        return dsl.select(
                        QUEUE_TRACK.QUEUE_TRACK_ID,
                        playOrder,
                        TRACK.TRACK_ID,
                        TRACK.NAME,
                        TRACK.DURATION,
                        TRACK.POSITION,
                        ARTIST.ARTIST_ID,
                        ARTIST.NAME,
                        RELEASE.RELEASE_ID,
                        RELEASE.NAME)
                .from(QUEUE_TRACK)
                .join(TRACK)
                .on(TRACK.TRACK_ID.eq(QUEUE_TRACK.TRACK_ID))
                .join(ARTIST)
                .on(ARTIST.ARTIST_ID.eq(TRACK.ARTIST_ID))
                .join(MEDIUM)
                .on(MEDIUM.MEDIUM_ID.eq(TRACK.MEDIUM_ID))
                .join(RELEASE)
                .on(RELEASE.RELEASE_ID.eq(MEDIUM.RELEASE_ID));
    }

    /**
     * The entries of the user's queue if it is in the shuffle mode, for the caller to filter and order. The mode is
     * read once for the statement rather than joined, so a branch for the other mode is dropped without reading any
     * entry.
     */
    private SelectConditionStep<Record10<UUID, Integer, UUID, String, Integer, Integer, UUID, String, UUID, String>>
            selectPageEntries(final UUID userId, final ShuffleMode shuffle, final Field<Integer> playOrder) {
        final var queueShuffle = field(select(QUEUE.SHUFFLE).from(QUEUE).where(QUEUE.USER_ID.eq(userId)));
        return selectEntries(playOrder).where(QUEUE_TRACK.USER_ID.eq(userId)).and(queueShuffle.eq(shuffle));
    }

    /**
     * @return the queue's shuffle anchor, read once for the statement
     */
    private static Field<Integer> loadShuffleAnchor(final UUID userId) {
        return field(select(QUEUE.SHUFFLE_ANCHOR).from(QUEUE).where(QUEUE.USER_ID.eq(userId)));
    }

    /**
     * @return where an entry plays: its position as queued, or when the queue is shuffled its shuffle order, with the
     * entries before the anchor moved past the end
     */
    private static Field<Integer> playOrder() {
        return when(QUEUE.SHUFFLE.eq(ShuffleMode.OFF), QUEUE_TRACK.POSITION)
                .when(QUEUE_TRACK.SHUFFLE_ORDER.ge(QUEUE.SHUFFLE_ANCHOR), QUEUE_TRACK.SHUFFLE_ORDER)
                .otherwise(QUEUE_TRACK.SHUFFLE_ORDER.plus(WRAPPED_ORDER));
    }

    /**
     * @return the anchor the queue has once it is set to the mode: turning shuffle on anchors it at the current entry
     */
    private static Field<Integer> shuffleAnchor(final ShuffleMode shuffle) {
        return switch (shuffle) {
            case OFF -> QUEUE.SHUFFLE_ANCHOR;
            case ON -> {
                final var current = field(select(QUEUE_TRACK.SHUFFLE_ORDER)
                        .from(QUEUE_TRACK)
                        .where(QUEUE_TRACK.QUEUE_TRACK_ID.eq(QUEUE.CURRENT_ENTRY_ID)));
                yield when(QUEUE.SHUFFLE.eq(ShuffleMode.OFF), coalesce(current, QUEUE.SHUFFLE_ANCHOR))
                        .otherwise(QUEUE.SHUFFLE_ANCHOR);
            }
        };
    }

    /**
     * @return the current entry, null when the queue has none
     */
    private Field<QueueEntry> currentEntry() {
        return multiset(selectEntries(playOrder()).where(QUEUE_TRACK.QUEUE_TRACK_ID.eq(QUEUE.CURRENT_ENTRY_ID)))
                .convertFrom(entries -> entries.map(Records.mapping(QueueEntry::new)).stream()
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Creates the user's queue if they have none, and locks it until the transaction ends.
     */
    private Queue lockQueue(final UUID userId) {
        dsl.insertInto(QUEUE, QUEUE.USER_ID)
                .values(userId)
                .onConflictDoNothing()
                .execute();
        return dsl.selectFrom(QUEUE).where(QUEUE.USER_ID.eq(userId)).forUpdate().fetchSingle(queueMapper::fromRecord);
    }

    /**
     * @param tracks each track to queue, with its position from 1 in the order it was queued in
     */
    private QueueDetails replaceFrom(
            final UUID userId, final Select<Record2<UUID, Integer>> tracks, final int startIndex) {
        lockQueue(userId);
        dsl.deleteFrom(QUEUE_TRACK).where(QUEUE_TRACK.USER_ID.eq(userId)).execute();
        insertEntries(userId, tracks.asTable("source", "track_id", "position"), startIndex);
        final var startEntryId = loadEntryId(userId, startIndex);
        updateCurrentEntry(userId, startEntryId);
        dsl.update(QUEUE)
                .set(QUEUE.SHUFFLE_ANCHOR, 1)
                .where(QUEUE.USER_ID.eq(userId))
                .execute();
        return loadDetails(userId);
    }

    private void insertEntries(final UUID userId, final Table<Record2<UUID, Integer>> tracks, final int startIndex) {
        final var trackId = tracks.field("track_id", UUID.class);
        final var position = tracks.field("position", Integer.class);
        dsl.insertInto(
                        QUEUE_TRACK,
                        QUEUE_TRACK.QUEUE_TRACK_ID,
                        QUEUE_TRACK.USER_ID,
                        QUEUE_TRACK.TRACK_ID,
                        QUEUE_TRACK.POSITION,
                        QUEUE_TRACK.SHUFFLE_ORDER)
                .select(select(
                                function("uuidv7", UUID.class),
                                val(userId),
                                trackId,
                                position,
                                shuffleOrder(position, startIndex))
                        .from(tracks))
                .execute();
    }

    /**
     * @return the entry queued at the position, from 0
     */
    private UUID loadEntryId(final UUID userId, final int index) {
        return dsl.select(QUEUE_TRACK.QUEUE_TRACK_ID)
                .from(QUEUE_TRACK)
                .where(QUEUE_TRACK.USER_ID.eq(userId))
                .and(QUEUE_TRACK.POSITION.eq(index + 1))
                .fetchOptional(QUEUE_TRACK.QUEUE_TRACK_ID)
                .orElseThrow(() -> new QueueStartOutOfRangeException(index));
    }

    /**
     * @return a random order of the entries, with the start first so that shuffling a queue plays it first
     */
    private static Field<Integer> shuffleOrder(final Field<Integer> position, final int startIndex) {
        return rowNumber().over(orderBy(when(position.eq(startIndex + 1), 0).otherwise(1), rand()));
    }

    private void requireInLibrary(final List<UUID> trackIds) {
        final var inLibrary = dsl.select(TRACK.TRACK_ID)
                .from(TRACK)
                .join(TRACK_TO_INGEST)
                .on(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(TRACK.TRACK_INGEST_ID))
                .where(TRACK.TRACK_ID.in(trackIds))
                .and(TRACK_TO_INGEST.STATE.eq(IngestState.COMPLETED))
                .fetchSet(TRACK.TRACK_ID);
        trackIds.stream()
                .filter(trackId -> !inLibrary.contains(trackId))
                .findFirst()
                .ifPresent(trackId -> {
                    throw new TrackNotFoundException(trackId);
                });
    }
}
