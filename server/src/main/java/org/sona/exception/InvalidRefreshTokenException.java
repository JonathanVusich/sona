package org.sona.exception;

/**
 * A refresh token that is unknown, expired or revoked, or whose user may no longer sign in.
 */
public final class InvalidRefreshTokenException extends SonaException {

    public InvalidRefreshTokenException() {
        super("The refresh token is invalid");
    }
}
