package org.sona.auth;

public enum PasswordChange {
    CHANGED,
    INCORRECT_CURRENT_PASSWORD,
    INVALID_NEW_PASSWORD,
    // Users who sign in through an OIDC provider have no password in Sona.
    MANAGED_BY_PROVIDER
}
