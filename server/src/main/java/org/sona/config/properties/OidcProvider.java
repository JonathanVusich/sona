package org.sona.config.properties;

import org.sona.model.enums.UserRole;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.List;
import java.util.Objects;

/**
 * An OIDC provider users can sign in through. Sona is a confidential client there, using the authorization code flow.
 *
 * @param id           Sona's name for the provider, used in its sign-in and callback URLs
 * @param name         the name shown on its sign-in button
 * @param issuerUri    the provider's issuer, exactly as it appears in the {@code iss} claim of its tokens
 * @param clientId     Sona's client ID at the provider
 * @param clientSecret Sona's client secret at the provider
 * @param scopes       the scopes Sona asks for; {@code offline_access} asks for the refresh token Sona checks the
 *                     user's session with
 * @param roleClaim    the claim listing the user's groups or roles at the provider
 * @param adminValues  values of the role claim that make the user an admin
 */
public record OidcProvider(
        String id,
        String name,
        String issuerUri,
        String clientId,
        String clientSecret,
        @DefaultValue({"openid", "profile", "email", "offline_access"}) List<String> scopes,
        @DefaultValue("groups") String roleClaim,
        @DefaultValue List<String> adminValues
) {

    /**
     * @return the role the user's groups or roles at this provider give them
     */
    public UserRole userRole(final OidcUser user) {
        final var claim = user.getClaimAsStringList(roleClaim);
        final var values = Objects.requireNonNullElse(claim, List.<String>of());
        final var admin = values.stream().anyMatch(adminValues::contains);
        if (admin) {
            return UserRole.ADMIN;
        }
        return UserRole.USER;
    }
}
