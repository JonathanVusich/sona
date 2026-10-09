package org.sona.auth;

import org.sona.model.tables.pojos.LocalUser;

import java.util.Optional;

public interface PasswordService {

    /**
     * Checks a local user's username and password when they log in.
     *
     * @return the user, or empty if the username is unknown, the password is wrong or the account is disabled
     */
    Optional<LocalUser> authenticate(String username, String password);

    /**
     * Changes the user's password, which ends a required password change and every session the user has.
     *
     * @return whether the password changed, or why not
     */
    PasswordChange change(SignedInUser user, String currentPassword, String newPassword);
}
