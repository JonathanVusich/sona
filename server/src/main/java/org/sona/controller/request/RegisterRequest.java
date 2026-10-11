package org.sona.controller.request;

/**
 * @param registrationToken the token Sona gave the user when they first signed in through their provider
 * @param username          the username they chose
 */
public record RegisterRequest(String registrationToken, String username) {
}
