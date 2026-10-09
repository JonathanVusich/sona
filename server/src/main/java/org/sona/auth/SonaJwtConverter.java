package org.sona.auth;

import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.UUID;

import static org.sona.auth.DefaultTokenService.ROLE_CLAIM;
import static org.sona.auth.DefaultTokenService.STATE_CLAIM;
import static org.sona.auth.DefaultTokenService.USERNAME_CLAIM;

/**
 * Reads the user from an access token issued by Sona. The token is short-lived, so the role and account state it
 * carries are trusted until it expires, and the database is checked again when it is refreshed.
 */
public final class SonaJwtConverter implements Converter<Jwt, UserAuthentication> {

    @Override
    public UserAuthentication convert(final Jwt jwt) {
        final var role = role(jwt.getClaimAsString(ROLE_CLAIM));
        final var state = state(jwt.getClaimAsString(STATE_CLAIM));
        final var userId = UUID.fromString(jwt.getSubject());
        final var user = new SignedInUser(userId, jwt.getClaimAsString(USERNAME_CLAIM), role, state);
        return new UserAuthentication(user, jwt);
    }

    private static UserRole role(final String claim) {
        return switch (claim) {
            case "ADMIN" -> UserRole.ADMIN;
            case "USER" -> UserRole.USER;
            case null, default -> throw new InvalidBearerTokenException("Unknown role " + claim);
        };
    }

    private static AccountState state(final String claim) {
        return switch (claim) {
            case "ACTIVE" -> AccountState.ACTIVE;
            case "PASSWORD_CHANGE_REQUIRED" -> AccountState.PASSWORD_CHANGE_REQUIRED;
            case null, default -> throw new InvalidBearerTokenException("Unknown account state " + claim);
        };
    }
}
