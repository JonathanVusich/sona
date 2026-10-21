package org.sona.exception;

/**
 * A password change whose new password is the current one.
 */
public final class UnchangedPasswordException extends SonaException {

    public UnchangedPasswordException() {
        super("The new password is the current one");
    }
}
