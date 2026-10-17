package org.sona.controller.response;

import java.util.List;
import java.util.UUID;

/**
 * Everything the artist page shows.
 *
 * @param country       ISO 3166-1 code of the artist's area, null when MusicBrainz doesn't know it
 * @param releaseGroups the artist's release groups in the library, newest first, and those with no known date last
 */
public record ArtistDetailsResponse(
        UUID artistId,
        String name,
        String country,
        List<ArtistReleaseGroupResponse> releaseGroups
) {
}
