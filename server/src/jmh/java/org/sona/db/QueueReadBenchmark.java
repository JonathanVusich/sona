package org.sona.db;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;

/**
 * Times what the player does while it plays, on queues as long as playlists get: reading the queue's state and a page
 * of its entries, and reporting the position and a jump to another entry.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class QueueReadBenchmark {

    private static final int PAGE_SIZE = 21;

    @Param({"10000", "100000", "500000"})
    public int queueSize;

    @Param({"OFF", "ON"})
    public String shuffle;

    private QueueBenchmarkDatabase database;
    private QueueDao dao;
    private UUID middleEntryId;

    @Setup
    public void fillQueue() {
        database = new QueueBenchmarkDatabase();
        dao = database.readDao();
        final var playlistId = database.playlistId(queueSize);
        database.inTransaction(inTransaction ->
                inTransaction.replaceWithPlaylist(QueueBenchmarkDatabase.USER_ID, playlistId, queueSize / 2));
        middleEntryId = dao.loadEntriesAfter(QueueBenchmarkDatabase.USER_ID, queueSize / 4, 1)
                .getFirst()
                .entryId();
        database.inTransaction(
                inTransaction -> inTransaction.updateCurrentEntry(QueueBenchmarkDatabase.USER_ID, middleEntryId));
        database.inTransaction(inTransaction -> {
            inTransaction.updateModes(QueueBenchmarkDatabase.USER_ID, ShuffleMode.valueOf(shuffle), RepeatMode.OFF);
            return null;
        });
    }

    @Benchmark
    public QueueDetails loadDetails() {
        return dao.loadDetails(QueueBenchmarkDatabase.USER_ID);
    }

    @Benchmark
    public Object pageFromStart() {
        return dao.loadEntriesAfter(QueueBenchmarkDatabase.USER_ID, 0, PAGE_SIZE);
    }

    @Benchmark
    public Object pageFromMiddle() {
        return dao.loadEntriesAfter(QueueBenchmarkDatabase.USER_ID, queueSize / 2, PAGE_SIZE);
    }

    @Benchmark
    public Object pageFromEnd() {
        return dao.loadEntriesAfter(QueueBenchmarkDatabase.USER_ID, queueSize - PAGE_SIZE, PAGE_SIZE);
    }

    @Benchmark
    public Object pageBeforeMiddle() {
        return dao.loadEntriesBefore(QueueBenchmarkDatabase.USER_ID, queueSize / 2, PAGE_SIZE);
    }

    @Benchmark
    public Object jumpToEntry() {
        return database.inTransaction(
                inTransaction -> inTransaction.updateCurrentEntry(QueueBenchmarkDatabase.USER_ID, middleEntryId));
    }

    @Benchmark
    public Object reportPosition() {
        return database.inTransaction(inTransaction -> {
            inTransaction.updatePositionSeconds(QueueBenchmarkDatabase.USER_ID, middleEntryId, 30);
            return null;
        });
    }
}
