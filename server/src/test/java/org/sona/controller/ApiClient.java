package org.sona.controller;

import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Calls Sona's API over HTTP, as the frontend does.
 */
public final class ApiClient {

    private final RestTestClient client;

    public ApiClient(final int port) {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    public RestTestClient.ResponseSpec get(final String path, final String accessToken) {
        return client.get().uri(path)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .exchange();
    }

    public RestTestClient.ResponseSpec getAnonymously(final String path) {
        return client.get().uri(path).exchange();
    }
}
