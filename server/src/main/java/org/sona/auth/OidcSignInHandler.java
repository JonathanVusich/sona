package org.sona.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sona.config.properties.AuthProperties;
import org.sona.config.properties.OidcProvider;
import org.sona.controller.response.ErrorCode;
import org.sona.model.enums.UserRole;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Finishes signing in through an OIDC provider, once Spring has checked the provider's ID token, and sends the
 * browser back to the web frontend: signed in with a refresh cookie, to its {@code register} page with a registration
 * token, or with an error code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class OidcSignInHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final OidcAccounts oidcAccounts;
    private final OidcClientRegistrations registrations;
    private final RefreshCookies refreshCookies;
    private final AuthProperties properties;

    @Override
    public void onAuthenticationSuccess(final HttpServletRequest request, final HttpServletResponse response,
                                        final Authentication authentication) throws IOException {
        endSignInSession(request);
        final var token = (OAuth2AuthenticationToken) authentication;
        final var providerId = token.getAuthorizedClientRegistrationId();
        final var user = providerUser(providerId, (OidcUser) token.getPrincipal());
        switch (oidcAccounts.signIn(user)) {
            case OidcSignIn.SignedIn signedIn -> {
                final var cookie = refreshCookies.issue(signedIn.tokens().refreshToken());
                response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
                response.sendRedirect(frontend().toUriString());
            }
            case OidcSignIn.RegistrationRequired required -> response.sendRedirect(register(required));
            case OidcSignIn.Disabled _ -> response.sendRedirect(error(ErrorCode.ACCOUNT_DISABLED));
        }
    }

    @Override
    public void onAuthenticationFailure(final HttpServletRequest request, final HttpServletResponse response,
                                        final AuthenticationException exception) throws IOException {
        endSignInSession(request);
        log.warn("Signing in through an OIDC provider failed", exception);
        response.sendRedirect(error(ErrorCode.SIGN_IN_FAILED));
    }

    /**
     * Spring kept the sign-in's state in a session while the user was at the provider. Sona's own tokens take over
     * from here, so the session ends.
     */
    private static void endSignInSession(final HttpServletRequest request) {
        final var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private ProviderUser providerUser(final String providerId, final OidcUser oidcUser) {
        final var provider = registrations.provider(providerId).orElseThrow();
        return new ProviderUser(providerId, provider.issuerUri(), oidcUser.getSubject(), role(provider, oidcUser));
    }

    private static UserRole role(final OidcProvider provider, final OidcUser user) {
        final var values = Objects.requireNonNullElse(user.getClaimAsStringList(provider.roleClaim()),
                List.<String>of());
        final var admin = values.stream().anyMatch(provider.adminValues()::contains);
        if (admin) {
            return UserRole.ADMIN;
        }
        return UserRole.USER;
    }

    private String register(final OidcSignIn.RegistrationRequired required) {
        // In the fragment, so the token never reaches a server or its logs.
        return frontend()
                .pathSegment("register")
                .fragment("registration_token=" + required.registrationToken())
                .toUriString();
    }

    private String error(final ErrorCode code) {
        return frontend().fragment("error=" + code.getCode()).toUriString();
    }

    private UriComponentsBuilder frontend() {
        return UriComponentsBuilder.fromUriString(properties.frontendUrl());
    }
}
