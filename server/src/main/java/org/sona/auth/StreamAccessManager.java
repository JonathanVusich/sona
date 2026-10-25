package org.sona.auth;

import static org.sona.auth.StreamTokenService.TRACK_CLAIM;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Allows a stream request only for the track its stream token was issued for.
 */
@RequiredArgsConstructor
public final class StreamAccessManager implements AuthorizationManager<RequestAuthorizationContext> {

    /**
     * The path variable that holds the track's ID.
     */
    private final String trackVariable;

    @Override
    public AuthorizationResult authorize(
            final Supplier<? extends Authentication> authentication, final RequestAuthorizationContext context) {
        return switch (authentication.get()) {
            case JwtAuthenticationToken stream -> {
                final var trackId = context.getVariables().get(trackVariable);
                final var streamedTrackId = stream.getToken().getClaimAsString(TRACK_CLAIM);
                yield new AuthorizationDecision(trackId != null && trackId.equals(streamedTrackId));
            }
            case null, default -> new AuthorizationDecision(false);
        };
    }
}
