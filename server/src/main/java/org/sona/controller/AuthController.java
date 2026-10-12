package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.auth.PasswordChecker;
import org.sona.auth.RefreshCookieService;
import org.sona.auth.TokenIssuer;
import org.sona.auth.Tokens;
import org.sona.config.properties.AuthProperties;
import org.sona.controller.request.LoginRequest;
import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.TokenResponse;
import org.sona.controller.response.TokenType;
import org.sona.exception.ClientException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logs local users in and out, and refreshes the access tokens Sona issues. The access token goes in the response
 * body, and the refresh token in an HttpOnly cookie that scripts can't read and that is only sent back here.
 */
@RestController
@RequiredArgsConstructor
public final class AuthController {

    private final PasswordChecker passwordChecker;
    private final TokenIssuer tokenIssuer;
    private final RefreshCookieService refreshCookies;
    private final AuthProperties properties;

    @PostMapping(ApiRoutes.AUTH_TOKEN)
    public ResponseEntity<TokenResponse> login(@RequestBody final LoginRequest request) {
        // The same answer for an unknown user, a wrong password or a disabled account, so none can be told apart.
        final var user = passwordChecker.checkLogin(request.username(), request.password())
                .orElseThrow(() -> new ClientException(ErrorCode.INVALID_CREDENTIALS));
        final var tokens = tokenIssuer.startSession(user);
        return tokenResponse(tokens);
    }

    @PostMapping(ApiRoutes.AUTH_REFRESH)
    public ResponseEntity<TokenResponse> refreshTokens(
            @CookieValue(name = RefreshCookieService.NAME, required = false) final String refreshToken) {
        if (refreshToken == null) {
            throw new ClientException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        final var tokens = tokenIssuer.refreshSession(refreshToken)
                .orElseThrow(() -> new ClientException(ErrorCode.INVALID_REFRESH_TOKEN));
        return tokenResponse(tokens);
    }

    @PostMapping(ApiRoutes.AUTH_LOGOUT)
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieService.NAME, required = false) final String refreshToken) {
        if (refreshToken == null || !tokenIssuer.endSession(refreshToken)) {
            throw new ClientException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        final var cookie = refreshCookies.clearCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(final Tokens tokens) {
        final var cookie = refreshCookies.issueCookie(tokens.refreshToken());
        final var body = new TokenResponse(tokens.accessToken(), TokenType.BEARER,
                properties.accessTokenLifetime().toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }
}
