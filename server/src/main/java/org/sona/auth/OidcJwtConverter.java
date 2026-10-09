package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.config.properties.OidcProvider;
import org.sona.model.enums.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;

import static org.sona.utils.IDGenerator.uuidv5;

/**
 * Reads the user from an OIDC provider's access token. The provider is trusted to say who the user is and which
 * groups they're in, so no database lookup is needed. The provider manages the account, including disabling it.
 * The user's Sona ID is worked out from their issuer and subject, so it is the same on every request.
 */
@RequiredArgsConstructor
public final class OidcJwtConverter implements Converter<Jwt, UserAuthentication> {

    private final OidcProvider provider;

    @Override
    public UserAuthentication convert(final Jwt jwt) {
        final var userId = uuidv5(provider.issuerUri() + "\n" + jwt.getSubject());
        final var user = new SignedInUser.Oidc(userId, provider.issuerUri(), jwt.getSubject(), username(jwt), role(jwt));
        return new UserAuthentication(user, jwt);
    }

    private String username(final Jwt jwt) {
        return Objects.requireNonNullElse(jwt.getClaimAsString(provider.usernameClaim()), jwt.getSubject());
    }

    private UserRole role(final Jwt jwt) {
        final var values = Objects.requireNonNullElse(jwt.getClaimAsStringList(provider.roleClaim()), List.<String>of());
        final var admin = values.stream().anyMatch(provider.adminValues()::contains);
        if (admin) {
            return UserRole.ADMIN;
        }
        return UserRole.USER;
    }
}
