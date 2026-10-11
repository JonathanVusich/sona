package org.sona.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param accessTokenLifetime  how long an access token issued by Sona stays valid
 * @param refreshTokenLifetime how long a refresh token stays valid. Each refresh replaces it with a new one.
 */
@ConfigurationProperties("auth")
public record AuthProperties(
        @DefaultValue("15m") Duration accessTokenLifetime,
        @DefaultValue("30d") Duration refreshTokenLifetime
) {
}
