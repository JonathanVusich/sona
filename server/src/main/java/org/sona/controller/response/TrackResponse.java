package org.sona.controller.response;

import java.util.UUID;

/**
 * @param duration    in seconds
 * @param discNumber  the release's medium the track is on, from 1
 * @param trackNumber the track's position on that medium, from 1
 * @param artist      who the track is by
 */
public record TrackResponse(
        UUID trackId,
        String name,
        int duration,
        int discNumber,
        int trackNumber,
        UUID releaseId,
        String releaseName,
        ArtistResponse artist
) {
}
