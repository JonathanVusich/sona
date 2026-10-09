package org.sona.auth;

import org.sona.model.tables.pojos.OidcUser;

public interface UserDirectory {

    /**
     * Finds the user from an OIDC provider behind a signed-in user. They are created on their first visit, and their
     * stored role follows the one their provider gives them.
     *
     * @return the user's row
     */
    OidcUser resolve(SignedInUser.Oidc user);
}
