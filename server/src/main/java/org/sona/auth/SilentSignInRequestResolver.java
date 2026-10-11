package org.sona.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.sona.controller.ApiRoutes;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Builds the request that sends the browser to an OIDC provider to sign in. With {@code ?prompt=none}, the provider is
 * asked not to show anything: it signs the user straight back in if they have a session there, and otherwise answers
 * {@code login_required} (OpenID Connect Core section 3.1.2.1).
 */
public final class SilentSignInRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String PROMPT = "prompt";
    private static final String NONE = "none";

    private final OAuth2AuthorizationRequestResolver delegate;

    public SilentSignInRequestResolver(final ClientRegistrationRepository registrations) {
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

    private static OAuth2AuthorizationRequest silentIfAsked(final HttpServletRequest request,
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
