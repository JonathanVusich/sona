package org.sona.exception;

/**
 * A password change for a user who signs in through an OIDC provider, and so has no password in Sona.
 */
public final class PasswordManagedByProviderException extends SonaException {

    public PasswordManagedByProviderException() {
        super("The user's password is managed by their provider");
    }
}
