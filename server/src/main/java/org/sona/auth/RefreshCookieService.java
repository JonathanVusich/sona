package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.config.properties.AuthProperties;
import org.sona.controller.ApiRoutes;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The HttpOnly cookie that carries a session's refresh token, which scripts can't read and which is only sent back to
 * Sona's auth endpoints.
 */
@Component
@RequiredArgsConstructor
public final class RefreshCookieService {

    public static final String NAME = "sona_refresh";

    private final AuthProperties properties;

    public ResponseCookie issue(final String refreshToken) {
        return cookie(refreshToken, properties.refreshTokenLifetime());
    }

    /**
     * @return a cookie that deletes the refresh cookie
     */
    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    private static ResponseCookie cookie(final String value, final Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(ApiRoutes.AUTH)
                .maxAge(maxAge)
                .build();
    }
}
