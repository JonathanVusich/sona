package org.sona.controller;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.sona.controller.response.TrackIngestResponse;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

/**
 * One client's STOMP connection, and the ERROR frame Sona answers it with, if any.
 */
public final class SocketConnection extends StompSessionHandlerAdapter {

    private static final long TIMEOUT_SECONDS = 5;

    private final CompletableFuture<StompSession> session = new CompletableFuture<>();
    private final CompletableFuture<String> error = new CompletableFuture<>();

    @Override
    public void afterConnected(final StompSession stompSession, final StompHeaders headers) {
        session.complete(stompSession);
    }

    @Override
    public Type getPayloadType(final StompHeaders headers) {
        return String.class;
    }

    /**
     * Only ERROR frames reach the session's own handler.
     */
    @Override
    public void handleFrame(final StompHeaders headers, final Object payload) {
        error.complete(headers.getFirst("message"));
    }

    /**
     * @return each ingest state change published from now on, in the order they arrive
     */
    public BlockingQueue<TrackIngestResponse> subscribeToProgress()
            throws ExecutionException, InterruptedException, TimeoutException {
        final var messages = new LinkedBlockingQueue<TrackIngestResponse>();
        awaitSession().subscribe(ApiRoutes.INGEST_PROGRESS, new StompFrameHandler() {
            @Override
            public Type getPayloadType(final StompHeaders headers) {
                return TrackIngestResponse.class;
            }

            @Override
            public void handleFrame(final StompHeaders headers, final Object payload) {
                if (payload instanceof TrackIngestResponse response) {
                    messages.add(response);
                }
            }
        });
        return messages;
    }

    /**
     * @return the message of the ERROR frame Sona sent
     */
    public String awaitError() throws ExecutionException, InterruptedException, TimeoutException {
        return error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private StompSession awaitSession() throws ExecutionException, InterruptedException, TimeoutException {
        return session.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
