package org.sona.config;

import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;

/**
 * Fails a page's request when it runs a second query. Each page has one endpoint, which reads everything it shows in
 * one query (AGENTS.md), and the sections that load after it each read through their own request. {@link
 * SingleQueryFilter} marks the requests to check.
 */
public final class SingleQueryListener implements ExecuteListener {

    // The request being served on this thread, while it's a page's, and the query it ran first.
    private static final ThreadLocal<String> REQUEST = new ThreadLocal<>();
    private static final ThreadLocal<String> FIRST_QUERY = new ThreadLocal<>();

    static void startRequest(final String request) {
        REQUEST.set(request);
    }

    static void finishRequest() {
        REQUEST.remove();
        FIRST_QUERY.remove();
    }

    @Override
    public void executeStart(final ExecuteContext context) {
        final var request = REQUEST.get();
        if (request == null) {
            return;
        }
        final var firstQuery = FIRST_QUERY.get();
        if (firstQuery == null) {
            FIRST_QUERY.set(context.sql());
            return;
        }
        throw new AssertionError("%s ran a second query, but a page reads everything it shows in one:%n%s%n%s"
                .formatted(request, firstQuery, context.sql()));
    }
}
