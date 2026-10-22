package org.sona.controller;

import static org.sona.auth.RefreshCookieService.NAME;

import java.net.HttpCookie;
import java.util.UUID;
import org.sona.controller.request.ChangePasswordRequest;
import org.sona.controller.request.LoginRequest;
import org.sona.controller.request.RegisterRequest;
import org.sona.controller.response.TokenResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Calls Sona's API over HTTP, as the frontend does.
 */
public final class ApiClient {

    /**
     * @param accessToken  sent as a bearer token
     * @param refreshToken sent back in the refresh cookie
     */
    public record Session(String accessToken, String refreshToken) {}

    private final RestTestClient client;

    public ApiClient(final int port) {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    public RestTestClient.ResponseSpec login(final String username, final String password) {
        return client.post()
                .uri(ApiRoutes.AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(username, password))
                .exchange();
    }

    /**
     * @return the session of a login that must succeed
     */
    public Session startSession(final String username, final String password) {
        return readSession(login(username, password));
    }

    public RestTestClient.ResponseSpec refreshSession(final String refreshToken) {
        return client.post()
                .uri(ApiRoutes.AUTH_REFRESH)
                .cookie(NAME, refreshToken)
                .exchange();
    }

    public RestTestClient.ResponseSpec logout(final String refreshToken) {
        return client.post()
                .uri(ApiRoutes.AUTH_LOGOUT)
                .cookie(NAME, refreshToken)
                .exchange();
    }

    public RestTestClient.ResponseSpec logoutWithoutCookie() {
        return client.post().uri(ApiRoutes.AUTH_LOGOUT).exchange();
    }

    public RestTestClient.ResponseSpec getWithToken(final String path, final String accessToken) {
        return client.get()
                .uri(path)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .exchange();
    }

    public RestTestClient.ResponseSpec getAnonymously(final String path) {
        return client.get().uri(path).exchange();
    }

    /**
     * Starts signing in through the OIDC provider, as the browser does when the user picks it.
     */
    public RestTestClient.ResponseSpec startOidcSignIn(final String providerId) {
        return client.get().uri(ApiRoutes.OIDC_AUTHORIZE + "/" + providerId).exchange();
    }

    /**
     * Starts signing in through the OIDC provider without letting it show anything, as the frontend does to sign a
     * user straight in when they still have a session there.
     */
    public RestTestClient.ResponseSpec startSilentOidcSignIn(final String providerId) {
        return client.get()
                .uri(ApiRoutes.OIDC_AUTHORIZE + "/" + providerId + "?prompt=none")
                .exchange();
    }

    /**
     * Comes back from the OIDC provider with an authorization code, as the browser does once the user signs in there.
     *
     * @param sessionCookie the cookie that started the sign-in, which holds its state
     */
    public RestTestClient.ResponseSpec finishOidcSignIn(
            final String providerId, final String code, final String state, final HttpCookie sessionCookie) {
        return client.get()
                .uri(uri -> uri.path(ApiRoutes.OIDC_CALLBACK + "/" + providerId)
                        .queryParam("code", code)
                        .queryParam("state", state)
                        .build())
                .cookie(sessionCookie.getName(), sessionCookie.getValue())
                .exchange();
    }

    /**
     * Comes back from the OIDC provider with an error, as the browser does when it couldn't sign the user in.
     */
    public RestTestClient.ResponseSpec failOidcSignIn(
            final String providerId, final String error, final String state, final HttpCookie sessionCookie) {
        return client.get()
                .uri(uri -> uri.path(ApiRoutes.OIDC_CALLBACK + "/" + providerId)
                        .queryParam("error", error)
                        .queryParam("state", state)
                        .build())
                .cookie(sessionCookie.getName(), sessionCookie.getValue())
                .exchange();
    }

    public RestTestClient.ResponseSpec registerUser(final String registrationToken, final String username) {
        return client.post()
                .uri(ApiRoutes.AUTH_REGISTER)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(registrationToken, username))
                .exchange();
    }

    public RestTestClient.ResponseSpec changePassword(
            final String accessToken, final String currentPassword, final String newPassword) {
        return client.post()
                .uri(ApiRoutes.CURRENT_USER_PASSWORD)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ChangePasswordRequest(currentPassword, newPassword))
                .exchange();
    }

    /**
     * Sends one file of an upload, as the frontend does: its bytes as the body, and its name as a parameter.
     */
    public RestTestClient.ResponseSpec uploadTrack(
            final String accessToken, final UUID groupIngestId, final String filename, final byte[] file) {
        return client.post()
                .uri(uri -> uri.path(ApiRoutes.INGEST_GROUP_TRACKS)
                        .queryParam("filename", filename)
                        .build(groupIngestId))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file)
                .exchange();
    }

    /**
     * Sends a file as curl does by default, as if it were a form.
     */
    public RestTestClient.ResponseSpec uploadTrackAsForm(
            final String accessToken, final UUID groupIngestId, final String filename, final byte[] file) {
        return client.post()
                .uri(uri -> uri.path(ApiRoutes.INGEST_GROUP_TRACKS)
                        .queryParam("filename", filename)
                        .build(groupIngestId))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(file)
                .exchange();
    }

    public RestTestClient.ResponseSpec uploadTrackWithoutFilename(
            final String accessToken, final UUID groupIngestId, final byte[] file) {
        return client.post()
                .uri(ApiRoutes.INGEST_GROUP_TRACKS, groupIngestId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file)
                .exchange();
    }

    /**
     * @return the tokens from a response that must have issued them
     */
    public static Session readSession(final RestTestClient.ResponseSpec response) {
        final var result =
                response.expectStatus().isOk().expectBody(TokenResponse.class).returnResult();
        return new Session(result.getResponseBody().accessToken(), refreshToken(result));
    }

    private static String refreshToken(final EntityExchangeResult<TokenResponse> result) {
        final var setCookie = result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return HttpCookie.parse(setCookie).getFirst().getValue();
    }
}
