package org.sona.controller;

import org.sona.controller.request.ChangePasswordRequest;
import org.sona.controller.request.LoginRequest;
import org.sona.controller.response.TokenResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.net.HttpCookie;

import static org.sona.controller.AuthController.REFRESH_COOKIE;

/**
 * Calls Sona's API over HTTP, as the frontend does.
 */
public final class ApiClient {

    /**
     * @param accessToken  sent as a bearer token
     * @param refreshToken sent back in the refresh cookie
     */
    public record Session(String accessToken, String refreshToken) {
    }

    private final RestTestClient client;

    public ApiClient(final int port) {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    public RestTestClient.ResponseSpec login(final String username, final String password) {
        return client.post().uri(ApiRoutes.AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(username, password))
                .exchange();
    }

    /**
     * @return the session of a login that must succeed
     */
    public Session session(final String username, final String password) {
        return session(login(username, password));
    }

    public RestTestClient.ResponseSpec refresh(final String refreshToken) {
        return client.post().uri(ApiRoutes.AUTH_REFRESH)
                .cookie(REFRESH_COOKIE, refreshToken)
                .exchange();
    }

    public RestTestClient.ResponseSpec logout(final String refreshToken) {
        return client.post().uri(ApiRoutes.AUTH_LOGOUT)
                .cookie(REFRESH_COOKIE, refreshToken)
                .exchange();
    }

    public RestTestClient.ResponseSpec logoutWithoutCookie() {
        return client.post().uri(ApiRoutes.AUTH_LOGOUT).exchange();
    }

    public RestTestClient.ResponseSpec get(final String path, final String accessToken) {
        return client.get().uri(path)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .exchange();
    }

    public RestTestClient.ResponseSpec getAnonymously(final String path) {
        return client.get().uri(path).exchange();
    }

    public RestTestClient.ResponseSpec changePassword(final String accessToken, final String currentPassword,
                                                      final String newPassword) {
        return client.post().uri(ApiRoutes.CURRENT_USER_PASSWORD)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ChangePasswordRequest(currentPassword, newPassword))
                .exchange();
    }

    /**
     * @return the tokens from a response that must have issued them
     */
    public static Session session(final RestTestClient.ResponseSpec response) {
        final var result = response.expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult();
        return new Session(result.getResponseBody().accessToken(), refreshToken(result));
    }

    private static String refreshToken(final EntityExchangeResult<TokenResponse> result) {
        final var setCookie = result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return HttpCookie.parse(setCookie).getFirst().getValue();
    }
}
