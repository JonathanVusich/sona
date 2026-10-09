package org.sona.auth;

import org.sona.model.tables.pojos.LocalUser;

import java.util.Optional;

public interface TokenService {

    /**
     * Starts a new session for the user.
     *
     * @return an access token and the first refresh token of the session
     */
    Tokens issue(LocalUser user);

    /**
     * Replaces the refresh token with a new one. Using a refresh token that was already replaced means it was copied,
     * so its whole session is revoked.
     *
     * @return new tokens, or empty if the refresh token is unknown, expired or revoked, or its user is disabled
     */
    Optional<Tokens> refresh(String refreshToken);

    /**
     * Ends the session the refresh token belongs to.
     */
    void revoke(String refreshToken);

    /**
     * Ends every session the user has.
     */
    void revokeAll(LocalUser user);
}
