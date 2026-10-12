package org.sona.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.sona.controller.ApiRoutes;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.sona.auth.OidcUtils.silentIfAsked;

/**
 * Builds the request that sends the browser to an OIDC provider to sign in. With {@code ?prompt=none}, the provider is
 * asked not to show anything: it signs the user straight back in if they have a session there, and otherwise answers
 * {@code login_required} (OpenID Connect Core section 3.1.2.1).
 */
public final class SilentSignInResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;

    public SilentSignInResolver(final ClientRegistrationRepository registrations) {
        delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, ApiRoutes.OIDC_AUTHORIZE);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(final HttpServletRequest request) {
        return silentIfAsked(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(final HttpServletRequest request, final String clientRegistrationId) {
        return silentIfAsked(request, delegate.resolve(request, clientRegistrationId));
    }
}
