package org.sona.config.properties;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param accessTokenLifetime  how long an access token issued by Sona stays valid
 * @param refreshTokenLifetime how long a refresh token stays valid. Each refresh replaces it with a new one.
 * @param frontendUrl          where the browser is sent after signing in through an OIDC provider: the web frontend,
 *                             whose {@code register} page finishes a new user's sign-up
 * @param oidcProviders        OIDC providers users can sign in through
 */
@ConfigurationProperties("auth")
public record AuthProperties(
        @DefaultValue("15m") Duration accessTokenLifetime,
        @DefaultValue("30d") Duration refreshTokenLifetime,
        @DefaultValue("/") String frontendUrl,
        @DefaultValue List<OidcProvider> oidcProviders) {}
