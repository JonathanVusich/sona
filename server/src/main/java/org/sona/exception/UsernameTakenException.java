package org.sona.exception;

/**
 * A registration whose chosen username another user has.
 */
public final class UsernameTakenException extends SonaException {

    public UsernameTakenException(final String username) {
        super("Username %s is taken".formatted(username));
    }
}
