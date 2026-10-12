package org.sona.config;

import org.jooq.Delete;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.Insert;
import org.jooq.Query;
import org.jooq.Select;
import org.jooq.Update;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;

import static org.sona.model.Sona.SONA;

/**
 * Fails any query that would scan a whole table of Sona's. Before each query runs, it asks Postgres for the plan with
 * sequential scans disabled, which Postgres then only picks when no index can serve the query. Every integration test
 * runs with it, so a query added without its index fails the first test that runs it.
 */
public final class IndexedQueryListener implements ExecuteListener {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public void executeStart(final ExecuteContext context) {
        switch (context.query()) {
            case Select<?> _, Insert<?> _, Update<?> _, Delete<?> _ -> checkPlan(context, context.query());
            // Batches, DDL and truncation have no plan to check.
            case null, default -> { }
        }
    }

    private static void checkPlan(final ExecuteContext context, final Query query) {
        final var sql = context.dsl().renderInlined(query);
        final var plan = explain(context.connection(), sql);
        // The plan is a list of one entry per statement, each with its tree of nodes under "Plan".
        final var scannedTables = plan.valueStream()
                .flatMap(entry -> sequentialScans(entry.path("Plan")))
                .toList();
        if (!scannedTables.isEmpty()) {
            throw new AssertionError("Query scans %s without an index:%n%s%n%s"
                    .formatted(scannedTables, sql, plan.toPrettyString()));
        }
    }

    private static JsonNode explain(final Connection connection, final String sql) {
        try (final var statement = connection.createStatement()) {
            statement.execute("set enable_seqscan = off");
            try (final var result = statement.executeQuery("explain (format json, verbose) " + sql)) {
                result.next();
                return JSON.readTree(result.getString(1));
            } finally {
                statement.execute("reset enable_seqscan");
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Couldn't explain " + sql, exception);
        }
    }

    /**
     * @return the tables of Sona's that a plan node, or any node below it, reads in full
     */
    private static Stream<String> sequentialScans(final JsonNode node) {
        final var own = Stream.of(node)
                .filter(candidate -> candidate.path("Node Type").asString("").equals("Seq Scan"))
                .filter(candidate -> candidate.path("Schema").asString("").equals(SONA.getName()))
                .map(candidate -> candidate.path("Relation Name").asString(""));
        final var below = node.path("Plans").valueStream().flatMap(IndexedQueryListener::sequentialScans);
        return Stream.concat(own, below);
    }
}
