package org.sona.controller.response;

import org.sona.model.enums.ReleaseGroupType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One of an artist's release groups, as the artist page lists them.
 *
 * @param type             null when MusicBrainz gives the release group no primary type
 * @param firstReleaseDate null when unknown, as on {@link ReleaseGroupResponse}
 */
public record ArtistReleaseGroupResponse(
        UUID releaseGroupId,
        String name,
        ReleaseGroupType type,
        LocalDate firstReleaseDate
) {
}
