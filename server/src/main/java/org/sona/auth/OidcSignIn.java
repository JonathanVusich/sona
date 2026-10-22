package org.sona.auth;

/**
 * What happened when a user signed in through an OIDC provider.
 */
public sealed interface OidcSignIn permits OidcSignIn.SignedIn, OidcSignIn.RegistrationRequired, OidcSignIn.Disabled {

    /**
     * The user has an account, and a new session.
     */
    record SignedIn(Tokens tokens) implements OidcSignIn {}

    /**
     * The user has no account yet. The registration token lets them create one with the username they choose.
     */
    record RegistrationRequired(String registrationToken) implements OidcSignIn {}

    /**
     * The user's account is disabled in Sona.
     */
    record Disabled() implements OidcSignIn {}
}
