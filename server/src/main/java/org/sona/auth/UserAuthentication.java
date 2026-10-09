package org.sona.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;

/**
 * A request authenticated by an access token. The token is trusted as it is, so its user's permissions follow from
 * the role it states.
 */
public final class UserAuthentication extends AbstractAuthenticationToken {

    private final SignedInUser user;
    private final Jwt token;
    private final Set<Permission> permissions;

    public UserAuthentication(final SignedInUser user, final Jwt token) {
        final var permissions = permissions(user);
        super(permissions);
        this.user = user;
        this.token = token;
        this.permissions = permissions;
        setAuthenticated(true);
    }

    public SignedInUser user() {
        return user;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    @Override
    public SignedInUser getPrincipal() {
        return user;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }

    @Override
    public String getName() {
        return user.username();
    }

    private static Set<Permission> permissions(final SignedInUser user) {
        return switch (user.state()) {
            case ACTIVE -> Permission.granted(user.role());
            // Until they change their password, the user may only do that.
            case PASSWORD_CHANGE_REQUIRED -> Set.of();
            // Sona never issues tokens to disabled users, so a token saying so is malformed.
            case DISABLED -> throw new DisabledException("User " + user.username() + " is disabled");
        };
    }
}
