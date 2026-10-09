package org.sona.config.properties;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * @param issuerUri     the provider's issuer, exactly as it appears in the {@code iss} claim of its tokens
 * @param audience      the audience the provider's tokens must be minted for (usually Sona's client ID there)
 * @param usernameClaim the claim holding the username given to a user created on their first sign-in
 * @param roleClaim     the claim listing the user's groups or roles at the provider
 * @param adminValues   values of the role claim that make the user an admin
 */
public record OidcProvider(
        String issuerUri,
        String audience,
        @DefaultValue("preferred_username") String usernameClaim,
        @DefaultValue("groups") String roleClaim,
        @DefaultValue List<String> adminValues
) {
}
