package org.sona.exception;

/**
 * A registration token that is unknown, expired or forged.
 */
public final class InvalidRegistrationTokenException extends SonaException {

    public InvalidRegistrationTokenException() {
        super("The registration token is invalid");
    }
}
