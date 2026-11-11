package org.sona.auth;

import org.sona.model.tables.pojos.OidcIdentity;
import org.sona.model.tables.pojos.OidcSession;

/**
 * Checks sessions of users from OIDC providers with their provider.
 */
public interface ProviderClient {

    /**
     * Refreshes the session at its provider, keeping the refresh token the provider rotates to.
     *
     * @param identity the identity at the provider of the session's user
     *
     * @return whether the provider still accepts the session, which it doesn't once it has disabled the user or ended
     * their session there
     * @throws org.springframework.security.oauth2.core.OAuth2AuthorizationException if the provider refuses the refresh
     *     token or can't be reached, which ends the session
     */
    boolean refreshSession(OidcIdentity identity, OidcSession session);
}
