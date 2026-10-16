package org.sona.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.Serial;
import java.util.Set;

/**
 * A request authenticated by an access token. The token is trusted as it is, so its user's permissions follow from
 * the role it states.
 */
public final class UserAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SignedInUser user;
    private final Jwt token;

    public UserAuthentication(final SignedInUser user, final Jwt token) {
        super(user.permissions());
        this.user = user;
        this.token = token;
        setAuthenticated(true);
    }

    public SignedInUser user() {
        return user;
    }

    public Set<Permission> permissions() {
        return user.permissions();
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
}
