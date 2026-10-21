package org.sona.exception;

import java.util.UUID;

/**
 * A release that isn't in the library.
 */
public final class ReleaseNotFoundException extends SonaException {

    public ReleaseNotFoundException(final UUID releaseId) {
        super("Release %s isn't in the library".formatted(releaseId));
    }
}
