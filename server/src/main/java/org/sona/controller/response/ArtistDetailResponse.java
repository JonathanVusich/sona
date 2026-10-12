package org.sona.controller.response;

import java.util.List;
import java.util.UUID;

/**
 * @param releaseGroups the artist's release groups, the most recently first released first
 * @param tracks        every song once: the tracks of each release group's main release
 */
public record ArtistDetailResponse(
        UUID artistId,
        String name,
        String country,
        List<ReleaseGroupResponse> releaseGroups,
        List<TrackResponse> tracks
) {
}
