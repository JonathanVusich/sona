package org.sona.controller.response;

import org.sona.model.enums.ReleaseGroupType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @param type             null when MusicBrainz gives the release group no primary type
 * @param firstReleaseDate the date of the group's earliest release anywhere, with the parts MusicBrainz doesn't know
 *                         as the first ("2002" is 2002-01-01), or null
 */
public record ReleaseGroupResponse(
        UUID releaseGroupId,
        String name,
        ReleaseGroupType type,
        LocalDate firstReleaseDate,
        ArtistResponse artist
) {
}
