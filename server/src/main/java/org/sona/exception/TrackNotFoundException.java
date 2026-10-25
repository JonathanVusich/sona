package org.sona.exception;

import java.util.UUID;

/**
 * A track that isn't in the library.
 */
public final class TrackNotFoundException extends SonaException {

    public TrackNotFoundException(final UUID trackId) {
        super("Track %s isn't in the library".formatted(trackId));
    }
}
