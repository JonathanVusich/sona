package org.sona.db;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.unnest;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.QUEUE;
import static org.sona.model.Tables.QUEUE_TRACK;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.TRACK;
import static org.sona.model.Tables.TRACK_TO_INGEST;
import static org.sona.utils.IDGenerator.uuidv7;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.sona.exception.TrackNotFoundException;
import org.sona.mappers.QueueMapper;
import org.sona.mappers.QueueTrackMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;
import org.sona.model.tables.pojos.Queue;
import org.sona.model.tables.pojos.QueueTrack;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users' play queues. Each user has one, which only they see, so every method takes the user. A queue's row is created
 * the first time its user changes it. Every change locks the row and raises its version, so changes made at the same
 * time happen one after the other.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class QueueDao {

    private final DSLContext dsl;
    private final QueueMapper queueMapper;
    private final QueueTrackMapper queueTrackMapper;

    /**
     * @return the user's queue with its entries in play order, an empty one if they have never changed theirs
     */
    public QueueDetails loadDetails(final UUID userId) {
        return dsl.select(
                        QUEUE.VERSION,
                        QUEUE.CURRENT_ENTRY_ID,
                        QUEUE.POSITION_SECONDS,
                        QUEUE.SHUFFLE,
                        QUEUE.REPEAT,
                        entries())
                .from(QUEUE)
                .where(QUEUE.USER_ID.eq(userId))
                .fetchOptional(Records.mapping(QueueDetails::new))
                .orElseGet(QueueDetails::empty);
    }

    /**
     * Empties the queue and fills it with the tracks, a new entry for each, and makes the one at {@code startIndex}
     * the current one, at the start. If the queue is shuffled, the entries after the current one play in a random
     * order.
     *
     * @param startIndex which of the tracks plays first, from 0
     * @return the queue as it is now
     * @throws TrackNotFoundException if a track isn't in the library
     */
    public QueueDetails replace(final UUID userId, final List<UUID> trackIds, final int startIndex) {
        final var queue = lockQueue(userId);
        requireInLibrary(trackIds);
        dsl.deleteFrom(QUEUE_TRACK).where(QUEUE_TRACK.USER_ID.eq(userId)).execute();
        final var entryIds = trackIds.stream().map(_ -> uuidv7()).toList();
        final var playOrder = playOrder(queue.shuffle(), entryIds, startIndex);
        insertEntries(userId, trackIds, entryIds, playOrder);
        updateCurrentEntry(userId, entryIds.get(startIndex));
        return loadDetails(userId);
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
     * Turning shuffle on puts the entries after the current one in a random order, and leaves the ones before it
     * where they are. Turning it off puts them all back in the order they were queued in.
     */
    public void updateModes(final UUID userId, final ShuffleMode shuffle, final RepeatMode repeat) {
        final var queue = lockQueue(userId);
        if (shuffle != queue.shuffle()) {
            reorderEntries(userId, queue, shuffle);
        }
        dsl.update(QUEUE)
                .set(QUEUE.SHUFFLE, shuffle)
                .set(QUEUE.REPEAT, repeat)
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
                .set(QUEUE.VERSION, QUEUE.VERSION.plus(1))
                .where(QUEUE.USER_ID.eq(userId))
                .execute();
        dsl.deleteFrom(QUEUE_TRACK).where(QUEUE_TRACK.USER_ID.eq(userId)).execute();
    }

    private Field<List<QueueEntry>> entries() {
        return multiset(select(
                                QUEUE_TRACK.QUEUE_TRACK_ID,
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
                        .on(RELEASE.RELEASE_ID.eq(MEDIUM.RELEASE_ID))
                        .where(QUEUE_TRACK.USER_ID.eq(QUEUE.USER_ID))
                        .orderBy(QUEUE_TRACK.PLAY_ORDER))
                .convertFrom(entries -> entries.map(Records.mapping(QueueEntry::new)));
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

    private void insertEntries(
            final UUID userId, final List<UUID> trackIds, final List<UUID> entryIds, final List<UUID> playOrder) {
        final var entries = IntStream.range(0, entryIds.size())
                .mapToObj(index -> new QueueTrack(
                        entryIds.get(index),
                        userId,
                        trackIds.get(index),
                        index + 1,
                        playOrder.indexOf(entryIds.get(index)) + 1))
                .map(queueTrackMapper::toRecord)
                .toList();
        dsl.batchInsert(entries).execute();
    }

    private void reorderEntries(final UUID userId, final Queue queue, final ShuffleMode shuffle) {
        switch (shuffle) {
            case ON -> shuffleAfterCurrent(userId, queue);
            case OFF ->
                dsl.update(QUEUE_TRACK)
                        .set(QUEUE_TRACK.PLAY_ORDER, QUEUE_TRACK.POSITION)
                        .where(QUEUE_TRACK.USER_ID.eq(userId))
                        .execute();
        }
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

    /**
     * @return the entries in the order they play: as queued, or with the start first and the rest shuffled
     */
    private static List<UUID> playOrder(final ShuffleMode shuffle, final List<UUID> entryIds, final int startIndex) {
        return switch (shuffle) {
            case OFF -> entryIds;
            case ON -> startThenShuffled(entryIds, startIndex);
        };
    }

    private static List<UUID> startThenShuffled(final List<UUID> entryIds, final int startIndex) {
        final var others = Stream.concat(
                        entryIds.subList(0, startIndex).stream(),
                        entryIds.subList(startIndex + 1, entryIds.size()).stream())
                .toList();
        return Stream.concat(Stream.of(entryIds.get(startIndex)), shuffled(others).stream())
                .toList();
    }

    private void shuffleAfterCurrent(final UUID userId, final Queue queue) {
        final var entryIds = dsl.select(QUEUE_TRACK.QUEUE_TRACK_ID)
                .from(QUEUE_TRACK)
                .where(QUEUE_TRACK.USER_ID.eq(userId))
                .orderBy(QUEUE_TRACK.PLAY_ORDER)
                .fetch(QUEUE_TRACK.QUEUE_TRACK_ID);
        // With no current entry, indexOf is -1 and every entry is shuffled.
        final var played = entryIds.indexOf(queue.currentEntryId()) + 1;
        final var shuffledOrder = Stream.concat(
                        entryIds.subList(0, played).stream(),
                        shuffled(entryIds.subList(played, entryIds.size())).stream())
                .toList();
        updatePlayOrder(shuffledOrder);
    }

    private void updatePlayOrder(final List<UUID> shuffledOrder) {
        // The entries, numbered in the order given.
        final var given =
                unnest(shuffledOrder.toArray(UUID[]::new)).withOrdinality().as("given", "entry_id", "given_order");
        dsl.update(QUEUE_TRACK)
                .set(
                        QUEUE_TRACK.PLAY_ORDER,
                        given.field("given_order", Long.class).cast(Integer.class))
                .from(given)
                .where(QUEUE_TRACK.QUEUE_TRACK_ID.eq(given.field("entry_id", UUID.class)))
                .execute();
    }

    private static List<UUID> shuffled(final List<UUID> entryIds) {
        final var copy = new ArrayList<>(entryIds);
        Collections.shuffle(copy);
        return List.copyOf(copy);
    }
}
