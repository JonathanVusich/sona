package org.sona.exception;

/**
 * A login whose username is unknown, whose password is wrong, or whose account is disabled. They
 * aren't told apart, so a login doesn't reveal which usernames exist.
 */
public final class InvalidCredentialsException extends SonaException {

    public InvalidCredentialsException() {
        super("The username or password is incorrect");
    }
}
