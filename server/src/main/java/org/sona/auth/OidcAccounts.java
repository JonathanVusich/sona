package org.sona.auth;

/**
 * The accounts of users who sign in through an OIDC provider.
 */
public interface OidcAccounts {

    /**
     * Signs in a user the provider has just authenticated. A known user gets a session whose role follows the one
     * the provider now gives them, and a new one gets a registration token instead.
     */
    OidcSignIn signIn(ProviderUser user);

    /**
     * Creates the account of a user with a registration token, with the username they chose. They then sign in
     * through their provider again to start a session.
     *
     * @return the user the registration token was issued for
     */
    ProviderUser register(String registrationToken, String username);
}
