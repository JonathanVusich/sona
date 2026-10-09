package org.sona.controller.response;

/**
 * @param accessToken the access token, sent as {@code Authorization: Bearer <token>}
 * @param expiresIn   seconds until the access token expires
 */
public record TokenResponse(String accessToken, TokenType tokenType, long expiresIn) {
}
