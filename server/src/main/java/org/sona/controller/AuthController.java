package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.auth.PasswordService;
import org.sona.auth.TokenService;
import org.sona.auth.Tokens;
import org.sona.config.properties.AuthProperties;
import org.sona.controller.request.LoginRequest;
import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.TokenResponse;
import org.sona.controller.response.TokenType;
import org.sona.exception.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Logs local users in and out, and refreshes the access tokens Sona issues. The access token goes in the response
 * body, and the refresh token in an HttpOnly cookie that scripts can't read and that is only sent back here.
 */
@RestController
@RequiredArgsConstructor
public final class AuthController {

    public static final String REFRESH_COOKIE = "sona_refresh";

    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final AuthProperties properties;

    @PostMapping(ApiRoutes.AUTH_TOKEN)
    public ResponseEntity<TokenResponse> login(@RequestBody final LoginRequest request) {
        // The same answer for an unknown user, a wrong password or a disabled account, so none can be told apart.
        final var user = passwordService.authenticate(request.username(), request.password())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        final var tokens = tokenService.issue(user);
        return issued(tokens);
    }

    @PostMapping(ApiRoutes.AUTH_REFRESH)
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) final String refreshToken) {
        if (refreshToken == null) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        final var tokens = tokenService.refresh(refreshToken)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issued(tokens);
    }

    @PostMapping(ApiRoutes.AUTH_LOGOUT)
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) final String refreshToken) {
        if (refreshToken == null || !tokenService.revoke(refreshToken)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        final var cookie = refreshCookie("", Duration.ZERO);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    private ResponseEntity<TokenResponse> issued(final Tokens tokens) {
        final var cookie = refreshCookie(tokens.refreshToken(), properties.refreshTokenLifetime());
        final var body = new TokenResponse(tokens.accessToken(), TokenType.BEARER,
                properties.accessTokenLifetime().toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    private static ResponseCookie refreshCookie(final String value, final Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(ApiRoutes.AUTH)
                .maxAge(maxAge)
                .build();
    }
}
