package org.sona.controller.response;

import org.sona.model.enums.ReleaseGroupType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param releases every release in the group, the earliest first: that one is the group's main release
 * @param tracks   the main release's tracks, which stand for the group's
 */
public record ReleaseGroupDetailResponse(
        UUID releaseGroupId,
        String name,
        ReleaseGroupType type,
        LocalDate firstReleaseDate,
        ArtistResponse artist,
        List<ReleaseResponse> releases,
        List<TrackResponse> tracks
) {
}
