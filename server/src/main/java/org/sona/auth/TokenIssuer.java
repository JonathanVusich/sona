package org.sona.auth;

import org.sona.model.tables.pojos.Users;

import java.util.Optional;

public interface TokenIssuer {

    /**
     * Starts a new session for the user.
     *
     * @return an access token and the first refresh token of the session
     */
    Tokens startSession(Users user);

    /**
     * Starts a new session for a user who signed in through an OIDC provider. The provider's refresh token is kept, so
     * the provider is asked whether the user may still sign in each time the session is refreshed.
     *
     * @return an access token and the first refresh token of the session
     */
    Tokens startSession(Users user, String providerRefreshToken);

    /**
     * Replaces the refresh token with a new one. Using a refresh token that was already replaced means it was copied,
     * so its whole session is revoked.
     *
     * @return new tokens, or empty if the refresh token is unknown, expired or revoked, or its user is disabled in
     * Sona or, for a user from an OIDC provider, refused by the provider
     */
    Optional<Tokens> refreshSession(String refreshToken);

    /**
     * Ends the session the refresh token belongs to. Using a refresh token that was already replaced means it was
     * copied, so its whole session is revoked then too.
     *
     * @return whether the refresh token was valid: known, unexpired and not yet revoked
     */
    boolean endSession(String refreshToken);

    /**
     * Ends every session the user has.
     */
    void endAllSessions(Users user);
}
