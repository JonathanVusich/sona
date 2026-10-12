package org.sona.metadata.resolver;

import org.sona.client.model.Artist;

import java.util.UUID;

/**
 * An artist matched to MusicBrainz.
 */
public record ResolvedArtist(
        UUID artistId,
        String name
) {

    public ResolvedArtist(final Artist artist) {
        this(artist.id(), artist.name());
    }
}
