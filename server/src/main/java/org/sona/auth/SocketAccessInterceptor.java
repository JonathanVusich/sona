package org.sona.auth;

import static org.sona.controller.ApiRoutes.INGEST_PROGRESS;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP connections with an access token in the CONNECT frame, and checks each subscription against the
 * user's permissions. As with the HTTP API, the token is trusted as it is; it's only checked when the client connects,
 * so the connection stays open after it expires.
 */
@Component
@RequiredArgsConstructor
public final class SocketAccessInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * What a user needs to subscribe to each destination. Any other is refused.
     */
    private static final Map<String, Permission> SUBSCRIPTIONS = Map.of(INGEST_PROGRESS, Permission.LIBRARY_UPLOAD);

    private final JwtDecoder tokenDecoder;
    private final SonaJwtConverter converter = new SonaJwtConverter();

    @Override
    public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
        final var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        // Heartbeats have no command.
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> checkSubscription(accessor);
            // Sona only pushes; clients have nothing to send it.
            case SEND -> throw new AccessDeniedException("Clients can't send messages");
            default -> {}
        }
        return message;
    }

    private UserAuthentication authenticate(final StompHeaderAccessor accessor) {
        final var header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new AuthenticationCredentialsNotFoundException("Connecting needs an access token");
        }
        return converter.convert(tokenDecoder.decode(header.substring(BEARER_PREFIX.length())));
    }

    private static void checkSubscription(final StompHeaderAccessor accessor) {
        final var destination = accessor.getDestination();
        if (destination == null || !SUBSCRIPTIONS.containsKey(destination)) {
            throw new AccessDeniedException("Unknown destination " + destination);
        }
        final var permission = SUBSCRIPTIONS.get(destination);
        if (!(accessor.getUser() instanceof UserAuthentication user)
                || user.user().state() != SessionState.ACTIVE
                || !user.permissions().contains(permission)) {
            throw new AccessDeniedException("Not allowed to subscribe to " + destination);
        }
    }
}
