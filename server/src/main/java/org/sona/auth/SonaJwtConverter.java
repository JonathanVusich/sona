package org.sona.auth;

import org.sona.model.enums.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.UUID;


/**
 * Reads the user from an access token issued by Sona. The token is short-lived, so the role and account state it
 * carries are trusted until it expires, and the database is checked again when it is refreshed.
 */
public final class SonaJwtConverter implements Converter<Jwt, UserAuthentication> {

    /**
     * The {@code iss} claim of the access tokens Sona issues.
     */
    public static final String ISSUER = "sona";

    public static final String USERNAME_CLAIM = "preferred_username";
    public static final String ROLE_CLAIM = "role";
    public static final String STATE_CLAIM = "session_state";

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

    private static SessionState state(final String claim) {
        return switch (claim) {
            case "ACTIVE" -> SessionState.ACTIVE;
            case "PASSWORD_CHANGE_REQUIRED" -> SessionState.PASSWORD_CHANGE_REQUIRED;
            case null, default -> throw new InvalidBearerTokenException("Unknown account state " + claim);
        };
    }
}
