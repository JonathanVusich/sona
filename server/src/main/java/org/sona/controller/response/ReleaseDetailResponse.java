package org.sona.controller.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param date    the parts MusicBrainz doesn't know are the first ("2002" is 2002-01-01), or null
 * @param country ISO 3166-1 code, or a MusicBrainz code like "XW" for worldwide, or null
 * @param labels  by name
 * @param formats one per medium, in order, like "CD" or "Digital Media", leaving out those MusicBrainz doesn't know
 * @param artist  who the release is by
 */
public record ReleaseDetailResponse(
        UUID releaseId,
        String name,
        LocalDate date,
        String country,
        List<String> labels,
        List<String> formats,
        UUID releaseGroupId,
        ArtistResponse artist,
        List<TrackResponse> tracks
) {
}
