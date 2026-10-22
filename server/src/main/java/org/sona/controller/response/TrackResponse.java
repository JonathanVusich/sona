package org.sona.controller.response;

import java.util.UUID;

/**
 * @param duration in seconds
 * @param position on its medium, from 1
 * @param artist   the track's own artist, who may not be the release's (as on a compilation)
 */
public record TrackResponse(UUID trackId, String name, int duration, int position, ArtistResponse artist) {}
