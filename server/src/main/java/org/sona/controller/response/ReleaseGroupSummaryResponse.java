package org.sona.controller.response;

import org.sona.model.enums.ReleaseGroupType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A release group as the list of albums shows it.
 *
 * @param type             null when MusicBrainz gives the release group no primary type
 * @param firstReleaseDate null when unknown, as on {@link ReleaseGroupResponse}
 */
public record ReleaseGroupSummaryResponse(
        UUID releaseGroupId,
        String name,
        ReleaseGroupType type,
        LocalDate firstReleaseDate,
        ArtistResponse artist
) {
}
