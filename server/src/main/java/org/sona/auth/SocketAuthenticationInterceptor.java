package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP connections with an access token in the CONNECT frame, as Spring's token authentication for
 * WebSockets does; {@link org.sona.config.SocketSecurityConfig} then checks each message against the user's
 * permissions. As with the HTTP API, the token is trusted as it is; it's only checked when the client connects, so the
 * connection stays open after it expires.
 */
@Component
@RequiredArgsConstructor
public final class SocketAuthenticationInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder tokenDecoder;
    private final SonaJwtConverter converter = new SonaJwtConverter();

    @Override
    public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
        final var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && accessor.getCommand() == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor));
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
}
