package org.sona.db;

import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.sona.mappers.QueueMapperImpl;

/**
 * The dev database, seeded with 500k tracks and playlists of 10k, 100k and 500k of them, that the queue benchmarks run
 * against.
 */
final class QueueBenchmarkDatabase {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private final DSLContext dsl;

    QueueBenchmarkDatabase() {
        dsl = DSL.using("jdbc:postgresql://localhost:5432/sona", "postgres", "12345");
    }

    DSLContext dsl() {
        return dsl;
    }

    /**
     * @return a DAO that runs each call as its own statement, as a read does outside a transaction
     */
    QueueDao readDao() {
        return new QueueDao(dsl, new QueueMapperImpl());
    }

    /**
     * Runs the DAO's call in a transaction, as Spring does for each of its methods.
     */
    <T> T inTransaction(final java.util.function.Function<QueueDao, T> call) {
        return dsl.transactionResult(
                configuration -> call.apply(new QueueDao(configuration.dsl(), new QueueMapperImpl())));
    }

    UUID playlistId(final int size) {
        return switch (size) {
            case 10_000 -> UUID.fromString("00000000-0000-0000-0000-0000000000c1");
            case 100_000 -> UUID.fromString("00000000-0000-0000-0000-0000000000c2");
            case 500_000 -> UUID.fromString("00000000-0000-0000-0000-0000000000c3");
            default -> throw new IllegalArgumentException("No playlist of " + size);
        };
    }
}
