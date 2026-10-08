package org.sona.metadata.resolver;

import java.util.UUID;

/**
 * An artist matched to MusicBrainz.
 */
public record ResolvedArtist(
        UUID artistId,
        String name
) {
}
