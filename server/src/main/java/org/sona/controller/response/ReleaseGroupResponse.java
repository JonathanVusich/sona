package org.sona.controller.response;

import org.sona.model.enums.ReleaseGroupType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the release group page shows.
 *
 * @param type             null when MusicBrainz gives the release group no primary type
 * @param firstReleaseDate the group's earliest release anywhere, null when unknown; like a release's date, the parts
 *                         MusicBrainz doesn't know are the first
 * @param editions         the group's releases in the library, earliest first, and those with no known date last
 */
public record ReleaseGroupResponse(
        UUID releaseGroupId,
        String name,
        ReleaseGroupType type,
        LocalDate firstReleaseDate,
        ArtistResponse artist,
        List<EditionResponse> editions
) {
}
