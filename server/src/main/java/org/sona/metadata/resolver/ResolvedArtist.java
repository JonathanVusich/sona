package org.sona.metadata.resolver;

import java.util.UUID;
import org.sona.client.model.Artist;

/**
 * An artist matched to MusicBrainz.
 *
 * @param country ISO 3166-1 code of the artist's area, or null when MusicBrainz doesn't know it or, in search
 *                results, doesn't say
 */
public record ResolvedArtist(UUID artistId, String name, String country) {

    public ResolvedArtist(final Artist artist) {
        this(artist.id(), artist.name(), artist.country());
    }
}
