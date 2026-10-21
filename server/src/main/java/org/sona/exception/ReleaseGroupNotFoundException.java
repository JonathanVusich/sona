package org.sona.exception;

import java.util.UUID;

/**
 * A release group that isn't in the library.
 */
public final class ReleaseGroupNotFoundException extends SonaException {

    public ReleaseGroupNotFoundException(final UUID releaseGroupId) {
        super("Release group %s isn't in the library".formatted(releaseGroupId));
    }
}
