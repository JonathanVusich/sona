package org.sona.db;

import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.deleteFrom;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.insertInto;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.orderBy;
import static org.jooq.impl.DSL.rand;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.unnest;
import static org.jooq.impl.DSL.update;
import static org.jooq.impl.DSL.val;
import static org.jooq.impl.DSL.when;
import static org.sona.db.TrackFields.inLibrary;
import static org.sona.db.TrackFields.trackSummary;
import static org.sona.model.Tables.PLAYLIST;
import static org.sona.model.Tables.PLAYLIST_TRACK;
import static org.sona.model.Tables.QUEUE;
import static org.sona.model.Tables.QUEUE_TRACK;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.TRACK;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertOnDuplicateStep;
import org.jooq.InsertReturningStep;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Records;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.sona.exception.PlaylistNotFoundException;
import org.sona.exception.QueueStartOutOfRangeException;
import org.sona.exception.ReleaseNotFoundException;
import org.sona.exception.TrackNotFoundException;
import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;
import org.sona.model.tables.records.QueueRecord;
import org.sona.model.tables.records.QueueTrackRecord;
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

    private static final Field<Long> NEXT_VERSION = QUEUE.VERSION.plus(1);

    private final DSLContext dsl;

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
        final var anchor = shuffleAnchor(userId);
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
        final var anchor = shuffleAnchor(userId);
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
        // The tracks given, numbered in the order given.
        final var given = unnest(trackIds.toArray(UUID[]::new)).withOrdinality().as("given", "track_id", "given_order");
        final var trackId = given.field("track_id", UUID.class);
        final var order = given.field("given_order", Long.class);
        final var missing = field(select(trackId)
                .from(given)
                .whereNotExists(selectOne()
                        .from(TRACK)
                        .where(TRACK.TRACK_ID.eq(trackId))
                        .and(inLibrary(TRACK)))
                .orderBy(order)
                .limit(1));
        lockQueue(userId).returningResult(missing).fetchOptional(missing).ifPresent(missingId -> {
            throw new TrackNotFoundException(missingId);
        });
        return replaceFrom(userId, select(trackId, order.cast(Integer.class)).from(given), startIndex);
    }

    /**
     * Fills the queue with the playlist's tracks, as {@link #replaceWithTracks} does.
     *
     * @throws PlaylistNotFoundException if the user doesn't have the playlist
     */
    public QueueDetails replaceWithPlaylist(final UUID userId, final UUID playlistId, final int startIndex) {
        final var owned = field(exists(selectOne()
                .from(PLAYLIST)
                .where(PLAYLIST.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST.USER_ID.eq(userId))));
        if (!lockQueue(userId).returningResult(owned).fetchSingle(owned)) {
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
        final var inLibrary = field(exists(selectOne()
                .from(RELEASE)
                .where(RELEASE.RELEASE_ID.eq(releaseId))
                .and(RELEASE.TRACK_COUNT.gt(0))));
        if (!lockQueue(userId).returningResult(inLibrary).fetchSingle(inLibrary)) {
            throw new ReleaseNotFoundException(releaseId);
        }
        final var medium = TRACK.medium();
        final var tracks = select(TRACK.TRACK_ID, rowNumber().over(orderBy(medium.POSITION, TRACK.POSITION)))
                .from(TRACK)
                .where(medium.RELEASE_ID.eq(releaseId))
                .and(inLibrary(TRACK));
        return replaceFrom(userId, tracks, startIndex);
    }

    /**
     * Moves the player to the entry, at its start.
     *
     * @return the queue's new version, if the entry is in the user's queue
     */
    public Optional<Long> setCurrentEntry(final UUID userId, final UUID entryId) {
        return dsl.update(QUEUE)
                .set(QUEUE.CURRENT_ENTRY_ID, entryId)
                .set(QUEUE.POSITION_SECONDS, 0)
                .set(QUEUE.VERSION, NEXT_VERSION)
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
    public void setPositionSeconds(final UUID userId, final UUID entryId, final int seconds) {
        dsl.update(QUEUE)
                .set(QUEUE.POSITION_SECONDS, seconds)
                .set(QUEUE.VERSION, NEXT_VERSION)
                .where(QUEUE.USER_ID.eq(userId))
                .and(QUEUE.CURRENT_ENTRY_ID.eq(entryId))
                .execute();
    }

    /**
     * Turning shuffle on starts the shuffled order from the current entry, and turning it off goes back to the order
     * the entries were queued in. Both orders are kept with the entries, so no entry changes.
     */
    public void setModes(final UUID userId, final ShuffleMode shuffle, final RepeatMode repeat) {
        // The update is a statement of its own, which reads the current entry once the lock is held.
        lockQueue(userId).execute();
        dsl.update(QUEUE)
                .set(QUEUE.SHUFFLE, shuffle)
                .set(QUEUE.REPEAT, repeat)
                .set(QUEUE.SHUFFLE_ANCHOR, anchorOnceSet(shuffle))
                .set(QUEUE.VERSION, NEXT_VERSION)
                .where(QUEUE.USER_ID.eq(userId))
                .execute();
    }

    /**
     * Empties the queue. Its shuffle and repeat modes stay as they are.
     */
    public void clear(final UUID userId) {
        final var removed = deleteFrom(QUEUE_TRACK)
                .where(QUEUE_TRACK.USER_ID.eq(userId))
                .returningResult(QUEUE_TRACK.QUEUE_TRACK_ID);
        dsl.with(name("removed").as(removed))
                .update(QUEUE)
                .setNull(QUEUE.CURRENT_ENTRY_ID)
                .set(QUEUE.POSITION_SECONDS, 0)
                .set(QUEUE.SHUFFLE_ANCHOR, 1)
                .set(QUEUE.VERSION, NEXT_VERSION)
                .where(QUEUE.USER_ID.eq(userId))
                .execute();
    }

    /**
     * The entries' columns, for the caller to filter and order.
     *
     * @param playOrder where each entry plays
     */
    private SelectJoinStep<Record3<UUID, Integer, TrackSummary>> selectEntries(final Field<Integer> playOrder) {
        return dsl.select(QUEUE_TRACK.QUEUE_TRACK_ID, playOrder, trackSummary(QUEUE_TRACK.track()))
                .from(QUEUE_TRACK);
    }

    /**
     * The entries of the user's queue if it is in the shuffle mode, for the caller to filter and order. The mode is
     * read once for the statement rather than joined, so a branch for the other mode is dropped without reading any
     * entry.
     */
    private SelectConditionStep<Record3<UUID, Integer, TrackSummary>> selectPageEntries(
            final UUID userId, final ShuffleMode shuffle, final Field<Integer> playOrder) {
        final var queueShuffle = field(select(QUEUE.SHUFFLE).from(QUEUE).where(QUEUE.USER_ID.eq(userId)));
        return selectEntries(playOrder).where(QUEUE_TRACK.USER_ID.eq(userId)).and(queueShuffle.eq(shuffle));
    }

    /**
     * @return the queue's shuffle anchor, read once for the statement
     */
    private static Field<Integer> shuffleAnchor(final UUID userId) {
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
    private static Field<Integer> anchorOnceSet(final ShuffleMode shuffle) {
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
     * Creates the user's queue if they have none, and locks it until the transaction ends. A change reads the queue's
     * entries in statements after this one, which see the changes made before the lock was held.
     *
     * @return the statement, for the caller to run, returning whatever it checks along with taking the lock
     */
    private InsertReturningStep<QueueRecord> lockQueue(final UUID userId) {
        return dsl.insertInto(QUEUE, QUEUE.USER_ID)
                .values(userId)
                .onConflict(QUEUE.USER_ID)
                .doUpdate()
                .set(QUEUE.USER_ID, excluded(QUEUE.USER_ID));
    }

    /**
     * Replaces the entries and starts the queue at the one at {@code startIndex}, in one statement, then reads the
     * queue in a statement of its own, as a statement can't read the rows it writes.
     *
     * @param tracks each track to queue, with its position from 1 in the order it was queued in
     */
    private QueueDetails replaceFrom(
            final UUID userId, final Select<Record2<UUID, Integer>> tracks, final int startIndex) {
        final var removed = name("removed")
                .as(deleteFrom(QUEUE_TRACK)
                        .where(QUEUE_TRACK.USER_ID.eq(userId))
                        .returningResult(QUEUE_TRACK.QUEUE_TRACK_ID));
        final var queued = name("queued")
                .as(insertEntries(userId, tracks.asTable("source", "track_id", "position"), startIndex)
                        .returningResult(QUEUE_TRACK.QUEUE_TRACK_ID, QUEUE_TRACK.POSITION));
        final var startEntry = field(select(queued.field(QUEUE_TRACK.QUEUE_TRACK_ID))
                .from(queued)
                .where(queued.field(QUEUE_TRACK.POSITION).eq(startIndex + 1)));
        final var started = name("started")
                .as(update(QUEUE)
                        .set(QUEUE.CURRENT_ENTRY_ID, startEntry)
                        .set(QUEUE.POSITION_SECONDS, 0)
                        .set(QUEUE.SHUFFLE_ANCHOR, 1)
                        .set(QUEUE.VERSION, NEXT_VERSION)
                        .where(QUEUE.USER_ID.eq(userId))
                        .returningResult(QUEUE.USER_ID));
        final var queuedCount =
                dsl.with(removed, queued, started).selectCount().from(queued).fetchSingle(0, int.class);
        if (startIndex >= queuedCount) {
            throw new QueueStartOutOfRangeException(startIndex);
        }
        return loadDetails(userId);
    }

    private static InsertOnDuplicateStep<QueueTrackRecord> insertEntries(
            final UUID userId, final Table<Record2<UUID, Integer>> tracks, final int startIndex) {
        final var trackId = tracks.field("track_id", UUID.class);
        final var position = tracks.field("position", Integer.class);
        return insertInto(
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
                        .from(tracks));
    }

    /**
     * @return a random order of the entries, with the start first so that shuffling a queue plays it first
     */
    private static Field<Integer> shuffleOrder(final Field<Integer> position, final int startIndex) {
        return rowNumber().over(orderBy(when(position.eq(startIndex + 1), 0).otherwise(1), rand()));
    }
}
