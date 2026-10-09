package org.sona.auth;

import org.sona.model.tables.pojos.Users;

public interface UserDirectory {

    /**
     * Finds the Sona user behind a signed-in user, for requests that need more than their token says.
     *
     * @return the user's row
     */
    Users resolve(SignedInUser user);
}
