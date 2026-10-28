package org.sona.db;

import static org.sona.model.Tables.PLAYLIST_TRACK;
import static org.sona.model.Tables.QUEUE;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;

/**
 * Times the statements that rewrite a whole queue, once each, as they are rare and long: replacing the queue from a
 * playlist, clearing it, and turning shuffle on and off. Before each run the queue is put in the state it needs.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, batchSize = 1)
@Measurement(iterations = 8, batchSize = 1)
@Fork(1)
public class QueueWriteBenchmark {

    private static final UUID USER_ID = QueueBenchmarkDatabase.USER_ID;

    @Param({"10000", "100000", "500000"})
    public int queueSize;

    private QueueBenchmarkDatabase database;
    private UUID playlistId;
    private List<UUID> thousandTracks;

    @Setup
    public void fillQueue() {
        database = new QueueBenchmarkDatabase();
        playlistId = database.playlistId(queueSize);
        thousandTracks = database.dsl()
                .select(PLAYLIST_TRACK.TRACK_ID)
                .from(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(playlistId))
                .orderBy(PLAYLIST_TRACK.POSITION)
                .limit(1000)
                .fetch(PLAYLIST_TRACK.TRACK_ID);
        replaceFromPlaylist();
    }

    @Setup(Level.Invocation)
    public void prepareQueue(final BenchmarkParams benchmark) {
        final var name = benchmark.getBenchmark();
        if (name.endsWith("replaceShuffleOn") || name.endsWith("turnShuffleOff")) {
            setShuffle(ShuffleMode.ON);
        }
        if (name.endsWith("replaceShuffleOff") || name.endsWith("clearQueue")) {
            setShuffle(ShuffleMode.OFF);
        }
        if (name.endsWith("turnShuffleOn")) {
            setShuffle(ShuffleMode.OFF);
        }
        if (name.endsWith("clearQueue") || name.endsWith("replaceFromThousandTracks")) {
            replaceFromPlaylist();
        }
    }

    @Benchmark
    public Object replaceShuffleOff() {
        return replaceFromPlaylist();
    }

    @Benchmark
    public Object replaceShuffleOn() {
        return replaceFromPlaylist();
    }

    @Benchmark
    public Object replaceFromThousandTracks() {
        return database.inTransaction(dao -> dao.replaceWithTracks(USER_ID, thousandTracks, 500));
    }

    @Benchmark
    public Object turnShuffleOn() {
        return setModes(ShuffleMode.ON);
    }

    @Benchmark
    public Object turnShuffleOff() {
        return setModes(ShuffleMode.OFF);
    }

    @Benchmark
    public Object clearQueue() {
        return database.inTransaction(dao -> {
            dao.delete(USER_ID);
            return null;
        });
    }

    private Object replaceFromPlaylist() {
        return database.inTransaction(dao -> dao.replaceWithPlaylist(USER_ID, playlistId, queueSize / 2));
    }

    private Object setModes(final ShuffleMode shuffle) {
        return database.inTransaction(dao -> {
            dao.updateModes(USER_ID, shuffle, RepeatMode.OFF);
            return null;
        });
    }

    private void setShuffle(final ShuffleMode shuffle) {
        database.dsl()
                .update(QUEUE)
                .set(QUEUE.SHUFFLE, shuffle)
                .where(QUEUE.USER_ID.eq(USER_ID))
                .execute();
    }
}
