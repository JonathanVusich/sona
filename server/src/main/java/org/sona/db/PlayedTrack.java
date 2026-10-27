package org.sona.db;

import java.util.UUID;

/**
 * A time a user played a track, with just what a list of plays shows: the track, its own artist, and the release it's
 * on.
 *
 * @param duration in seconds
 * @param position on its medium, from 1
 */
public record PlayedTrack(
        UUID playId,
        UUID trackId,
        String trackName,
        int duration,
        int position,
        UUID artistId,
        String artistName,
        UUID releaseId,
        String releaseName) {}
