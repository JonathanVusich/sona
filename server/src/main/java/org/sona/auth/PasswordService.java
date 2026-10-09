package org.sona.auth;

import org.sona.model.tables.pojos.LocalUser;

public interface PasswordService {

    /**
     * Changes the user's password, which ends a required password change and every session the user has.
     *
     * @return whether the password changed, or why not
     */
    PasswordChange change(LocalUser user, String currentPassword, String newPassword);
}
