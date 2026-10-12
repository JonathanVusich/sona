package org.sona.auth;

import lombok.experimental.UtilityClass;
import org.sona.controller.ApiRoutes;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * The cookies Sona sets.
 */
@UtilityClass
public final class CookieUtils {

    public static final String REFRESH_COOKIE = "sona_refresh";

    /**
     * @return the HttpOnly cookie that carries a refresh token, which scripts can't read and which is only sent back
     * to Sona's auth endpoints
     */
    public static ResponseCookie refreshCookie(final String refreshToken, final Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(ApiRoutes.AUTH)
                .maxAge(maxAge)
                .build();
    }
}
