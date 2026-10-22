package org.sona.controller;

import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * Answers a STOMP frame Sona refuses (a connection without a valid token, a subscription the user may not make) with
 * an ERROR frame, after which the connection is closed.
 */
public final class SocketErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    protected Message<byte[]> handleInternal(
            final StompHeaderAccessor errorHeaderAccessor,
            final byte[] errorPayload,
            @Nullable final Throwable cause,
            @Nullable final StompHeaderAccessor clientHeaderAccessor) {
        // Spring wraps what refused the frame in an exception whose message repeats the whole frame, token and all.
        if (cause instanceof MessageDeliveryException && cause.getCause() != null) {
            errorHeaderAccessor.setMessage(cause.getCause().getMessage());
        }
        return super.handleInternal(errorHeaderAccessor, errorPayload, cause, clientHeaderAccessor);
    }
}
