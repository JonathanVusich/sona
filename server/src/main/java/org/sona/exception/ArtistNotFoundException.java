package org.sona.exception;

import java.util.UUID;

/**
 * An artist who isn't in the library.
 */
public final class ArtistNotFoundException extends SonaException {

    public ArtistNotFoundException(final UUID artistId) {
        super("Artist %s isn't in the library".formatted(artistId));
    }
}
