package org.sona.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

/**
 * Holds the tokens a provider returns at sign-in for the rest of that request only, so {@link OidcSignInHandler} can
 * keep its refresh token. Spring's default would keep every signed-in user's tokens in memory for good.
 */
@Component
public final class RequestAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    private static final String ATTRIBUTE = RequestAuthorizedClientRepository.class.getName();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(final String clientRegistrationId,
                                                                     final Authentication principal,
                                                                     final HttpServletRequest request) {
        return switch (request.getAttribute(ATTRIBUTE)) {
            case OAuth2AuthorizedClient client
                    when client.getClientRegistration().getRegistrationId().equals(clientRegistrationId) -> (T) client;
            case null, default -> null;
        };
    }

    @Override
    public void saveAuthorizedClient(final OAuth2AuthorizedClient authorizedClient, final Authentication principal,
                                     final HttpServletRequest request, final HttpServletResponse response) {
        request.setAttribute(ATTRIBUTE, authorizedClient);
    }

    @Override
    public void removeAuthorizedClient(final String clientRegistrationId, final Authentication principal,
                                       final HttpServletRequest request, final HttpServletResponse response) {
        request.removeAttribute(ATTRIBUTE);
    }
}
