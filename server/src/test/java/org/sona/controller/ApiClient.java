package org.sona.controller;

import org.sona.controller.response.TokenResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.net.HttpCookie;

import static org.sona.auth.RefreshCookies.NAME;

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

    public RestTestClient.ResponseSpec refresh(final String refreshToken) {
        return client.post().uri(ApiRoutes.AUTH_REFRESH)
                .cookie(NAME, refreshToken)
                .exchange();
    }

    public RestTestClient.ResponseSpec logout(final String refreshToken) {
        return client.post().uri(ApiRoutes.AUTH_LOGOUT)
                .cookie(NAME, refreshToken)
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
