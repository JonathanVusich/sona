package org.sona.exception;

/**
 * A request Sona refuses for a reason of its own, such as a release that isn't in the library or a wrong password.
 * Services and controllers throw these rather than deciding on HTTP responses, and {@code ApiExceptionHandler}
 * answers each kind with its error code. The hierarchy is sealed, so a new kind doesn't compile until it has one.
 */
public abstract sealed class SonaException extends RuntimeException
        permits ReleaseNotFoundException,
                ReleaseGroupNotFoundException,
                ArtistNotFoundException,
                TrackNotFoundException,
                PlaylistNotFoundException,
                QueueEntryNotFoundException,
                IngestGroupNotFoundException,
                FileTooLargeException,
                InvalidCredentialsException,
                InvalidRefreshTokenException,
                IncorrectPasswordException,
                UnchangedPasswordException,
                PasswordManagedByProviderException,
                InvalidRegistrationTokenException,
                AlreadyRegisteredException,
                UsernameTakenException {

    protected SonaException(final String message) {
        super(message);
    }
}
