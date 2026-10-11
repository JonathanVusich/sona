package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.auth.RefreshCookies;
import org.sona.auth.TokenService;
import org.sona.auth.Tokens;
import org.sona.config.properties.AuthProperties;
import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.TokenResponse;
import org.sona.controller.response.TokenType;
import org.sona.exception.ClientException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Refreshes and ends the sessions Sona issues. The access token goes in the response
 * body, and the refresh token in an HttpOnly cookie that scripts can't read and that is only sent back here.
 */
@RestController
@RequiredArgsConstructor
public final class AuthController {

    private final TokenService tokenService;
    private final RefreshCookies refreshCookies;
    private final AuthProperties properties;

    @PostMapping(ApiRoutes.AUTH_REFRESH)
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) final String refreshToken) {
        if (refreshToken == null) {
            throw new ClientException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        final var tokens = tokenService.refresh(refreshToken)
                .orElseThrow(() -> new ClientException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issued(tokens);
    }

    @PostMapping(ApiRoutes.AUTH_LOGOUT)
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) final String refreshToken) {
        if (refreshToken == null || !tokenService.revoke(refreshToken)) {
            throw new ClientException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        final var cookie = refreshCookies.clear();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    private ResponseEntity<TokenResponse> issued(final Tokens tokens) {
        final var cookie = refreshCookies.issue(tokens.refreshToken());
        final var body = new TokenResponse(tokens.accessToken(), TokenType.BEARER,
                properties.accessTokenLifetime().toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }
}
