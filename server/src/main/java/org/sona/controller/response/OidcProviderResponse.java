package org.sona.controller.response;

/**
 * An OIDC provider users can sign in through.
 *
 * @param id   the provider's ID, which signing in through it starts from
 * @param name the name to show on its sign-in button
 */
public record OidcProviderResponse(String id, String name) {
}
