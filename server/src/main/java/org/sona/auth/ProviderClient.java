package org.sona.auth;

import org.sona.model.tables.pojos.OidcSession;

/**
 * Checks sessions of users from OIDC providers with their provider.
 */
public interface ProviderClient {

    /**
     * Refreshes the session at its provider, keeping the refresh token the provider rotates to.
     *
     * @return whether the provider still accepts the session, which it doesn't once it has disabled the user or ended
     * their session there
     */
    boolean refresh(OidcSession session);
}
