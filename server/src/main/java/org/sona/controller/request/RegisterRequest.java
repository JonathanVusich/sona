package org.sona.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * @param registrationToken the token Sona gave the user when they first signed in through their provider
 * @param username          the username they chose
 */
public record RegisterRequest(@NotBlank String registrationToken, @NotBlank String username) {
}
