package org.sona.exception;

/**
 * A password change whose current password is wrong.
 */
public final class IncorrectPasswordException extends SonaException {

    public IncorrectPasswordException() {
        super("The current password is incorrect");
    }
}
