package org.sona.exception;

/**
 * A registration for a provider user who already has an account.
 */
public final class AlreadyRegisteredException extends SonaException {

    public AlreadyRegisteredException() {
        super("The user already has an account");
    }
}
