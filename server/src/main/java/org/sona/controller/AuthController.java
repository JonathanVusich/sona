package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.auth.TokenService;
import org.sona.auth.Tokens;
import org.sona.config.properties.AuthProperties;
import org.sona.controller.request.LoginRequest;
import org.sona.controller.response.TokenResponse;
import org.sona.controller.response.TokenType;
import org.sona.db.UserDao;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    private final AuthenticationManager passwordAuthentication;
    private final TokenService tokenService;
    private final UserDao userDao;
    private final AuthProperties properties;

    @PostMapping(ApiRoutes.AUTH_TOKEN)
    public ResponseEntity<TokenResponse> login(@RequestBody final LoginRequest request) {
        final var credentials = UsernamePasswordAuthenticationToken.unauthenticated(request.username(),
                request.password());
        final var authentication = passwordAuthentication.authenticate(credentials);
        final var user = userDao.findByUsername(authentication.getName()).orElseThrow();
        final var tokens = tokenService.issue(user);
        return issued(tokens);
    }

    @PostMapping(ApiRoutes.AUTH_REFRESH)
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) final String refreshToken) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return tokenService.refresh(refreshToken)
                .map(this::issued)
                .orElseGet(() -> cleared(HttpStatus.UNAUTHORIZED));
    }

    @PostMapping(ApiRoutes.AUTH_LOGOUT)
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) final String refreshToken) {
        if (refreshToken != null) {
            tokenService.revoke(refreshToken);
        }
        return cleared(HttpStatus.NO_CONTENT);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> loginFailed(final AuthenticationException exception) {
        // The same answer for an unknown user, a wrong password or a disabled account, so none can be told apart.
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    private ResponseEntity<TokenResponse> issued(final Tokens tokens) {
        final var cookie = refreshCookie(tokens.refreshToken(), properties.refreshTokenLifetime());
        final var body = new TokenResponse(tokens.accessToken(), TokenType.BEARER,
                properties.accessTokenLifetime().toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    /**
     * @return a response with the status that also deletes the refresh cookie
     */
    private <T> ResponseEntity<T> cleared(final HttpStatus status) {
        final var cookie = refreshCookie("", Duration.ZERO);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
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
