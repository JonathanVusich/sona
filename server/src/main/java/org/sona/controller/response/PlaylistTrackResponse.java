package org.sona.controller.response;

import java.util.UUID;

/**
 * A track on a playlist: the fields of a {@link TrackResponse}, with the release it's on.
 *
 * @param duration in seconds
 * @param position on its medium, from 1; the playlist's own order is the order of its tracks
 * @param artist   the track's own artist
 */
public record PlaylistTrackResponse(
        UUID trackId, String name, int duration, int position, ArtistResponse artist, TrackReleaseResponse release) {}
