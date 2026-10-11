package org.sona.auth;

/**
 * @param accessToken  a short-lived JWT, sent as a bearer token
 * @param refreshToken an opaque token that gets the next access token
 */
public record Tokens(String accessToken, String refreshToken) {
}
