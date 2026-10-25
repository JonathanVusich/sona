package org.sona.controller.response;

/**
 * @param token sent as the {@code access_token} parameter of the track's stream URL, which it alone may stream
 */
public record StreamTokenResponse(String token) {}
