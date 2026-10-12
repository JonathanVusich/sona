package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.config.properties.AuthProperties;
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

    public static final String NAME = CookieUtils.REFRESH_COOKIE;

    private final AuthProperties properties;

    public ResponseCookie issueCookie(final String refreshToken) {
        return CookieUtils.refreshCookie(refreshToken, properties.refreshTokenLifetime());
    }

    /**
     * @return a cookie that deletes the refresh cookie
     */
    public ResponseCookie clearCookie() {
        return CookieUtils.refreshCookie("", Duration.ZERO);
    }
}
