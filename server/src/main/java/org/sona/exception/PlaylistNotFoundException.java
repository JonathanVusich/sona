package org.sona.exception;

import java.util.UUID;

/**
 * A playlist that doesn't exist, or is someone else's.
 */
public final class PlaylistNotFoundException extends SonaException {

    public PlaylistNotFoundException(final UUID playlistId) {
        super("Playlist %s isn't one of the user's".formatted(playlistId));
    }
}
