package org.sona.db;

import java.util.UUID;

/**
 * A track on a playlist, with just what the playlist page shows: its own artist and the release it's on.
 *
 * @param duration in seconds
 * @param position on its medium, from 1
 */
public record PlaylistEntry(
        UUID trackId,
        String trackName,
        int duration,
        int position,
        UUID artistId,
        String artistName,
        UUID releaseId,
        String releaseName) {}
