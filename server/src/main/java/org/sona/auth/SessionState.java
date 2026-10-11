package org.sona.auth;

/**
 * What a signed-in user's session allows, carried in Sona's access tokens.
 */
public enum SessionState {
    ACTIVE,
    // Until they change their password, the user may only do that.
    PASSWORD_CHANGE_REQUIRED
}
