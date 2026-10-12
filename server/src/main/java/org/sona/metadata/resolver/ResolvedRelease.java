package org.sona.metadata.resolver;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A MusicBrainz release with the details Sona stores.
 *
 * @param date    the parts MusicBrainz doesn't know are the first ("2002" is 2002-01-01), or null
 * @param country ISO 3166-1 code, or a MusicBrainz code like "XW" for worldwide, or null
 * @param media   in order
 */
public record ResolvedRelease(
        UUID releaseId,
        String title,
        LocalDate date,
        String country,
        List<ResolvedLabel> labels,
        List<ResolvedMedium> media,
        ResolvedArtist artist,
        ResolvedReleaseGroup releaseGroup
) {
}
