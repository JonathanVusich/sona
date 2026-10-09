package org.sona.auth;

import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;

import java.util.UUID;

/**
 * Who made a request, as their access token says. Nothing here is looked up in the database.
 */
public sealed interface SignedInUser permits SignedInUser.Local, SignedInUser.Oidc {

    UUID userId();

    String username();

    UserRole role();

    AccountState state();

    /**
     * A local user, signed in with a token Sona issued.
     *
     * @param userId the user's Sona ID, the token's subject
     */
    record Local(UUID userId, String username, UserRole role, AccountState state) implements SignedInUser {
    }

    /**
     * A user signed in with an OIDC provider's token.
     *
     * @param userId  the user's Sona ID, worked out from the issuer and subject
     * @param issuer  the provider's issuer
     * @param subject the user's ID at the provider
     */
    record Oidc(UUID userId, String issuer, String subject, String username, UserRole role) implements SignedInUser {

        /**
         * The provider manages the account, including disabling it, so a user with a valid token is active.
         */
        @Override
        public AccountState state() {
            return AccountState.ACTIVE;
        }
    }
}
