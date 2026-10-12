package org.sona.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.sona.model.tables.pojos.OidcSession;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Requests Sona sends to OIDC providers.
 */
@UtilityClass
public final class OidcUtils {

    private static final String PROMPT = "prompt";
    private static final String NONE = "none";

    /**
     * @return a request that refreshes the session at its provider with the provider's refresh token
     */
    public static OAuth2RefreshTokenGrantRequest refreshGrantRequest(final ClientRegistration registration,
                                                                     final OidcSession session) {
        // Sona doesn't keep the provider's access token, and the grant request only reads its scopes.
        final var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "unused", null, null,
                registration.getScopes());
        final var refreshToken = new OAuth2RefreshToken(session.refreshToken(), null);
        return new OAuth2RefreshTokenGrantRequest(registration, accessToken, refreshToken);
    }

    /**
     * @return the authorization request, asking the provider not to show anything if the request asked for
     * {@code prompt=none}
     */
    public static OAuth2AuthorizationRequest silentIfAsked(final HttpServletRequest request,
                                                           final OAuth2AuthorizationRequest authorization) {
        // Only none is passed on, so a link can't make the provider force a login or a consent screen.
        if (authorization == null || !NONE.equals(request.getParameter(PROMPT))) {
            return authorization;
        }
        return OAuth2AuthorizationRequest.from(authorization)
                .additionalParameters(parameters -> parameters.put(PROMPT, NONE))
                .build();
    }
}
