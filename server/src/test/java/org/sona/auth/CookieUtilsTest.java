package org.sona.auth;

import org.junit.jupiter.api.Test;
import org.sona.controller.ApiRoutes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilsTest {

    @Test
    void refreshCookieIsHiddenFromScriptsAndOnlySentToAuthEndpoints() {
        final var cookie = CookieUtils.refreshCookie("token", Duration.ofDays(30));

        assertThat(cookie.getName()).isEqualTo(CookieUtils.REFRESH_COOKIE);
        assertThat(cookie.getValue()).isEqualTo("token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo(ApiRoutes.AUTH);
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(30));
    }
}
