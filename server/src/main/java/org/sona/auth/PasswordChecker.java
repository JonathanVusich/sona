package org.sona.auth;

import org.sona.exception.IncorrectPasswordException;
import org.sona.exception.InvalidCredentialsException;
import org.sona.exception.PasswordManagedByProviderException;
import org.sona.exception.UnchangedPasswordException;
import org.sona.model.tables.pojos.Users;

public interface PasswordChecker {

    /**
     * Checks a local user's username and password when they log in.
     *
     * @return the user
     * @throws InvalidCredentialsException if the username is unknown, the password is wrong or the account is disabled
     */
    Users checkLogin(String username, String password);

    /**
     * Changes the user's password, which ends a required password change and every session the user has.
     *
     * @throws IncorrectPasswordException         if the current password is wrong
     * @throws UnchangedPasswordException         if the new password is the current one
     * @throws PasswordManagedByProviderException if the user signs in through an OIDC provider
     */
    void changePassword(SignedInUser user, String currentPassword, String newPassword);
}
