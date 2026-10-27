package org.sona.controller.response;

import java.util.UUID;

/**
 * A track in the play history: the fields of a {@link TrackResponse}, with the release it's on.
 *
 * @param duration in seconds
 * @param position on its medium, from 1
 * @param artist   the track's own artist
 */
public record PlayedTrackResponse(
        UUID trackId, String name, int duration, int position, ArtistResponse artist, TrackReleaseResponse release) {}
