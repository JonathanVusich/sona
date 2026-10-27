package org.sona.db;

import java.util.UUID;

/**
 * A track in a user's queue, with just what the queue shows: its own artist and the release it's on.
 *
 * @param duration in seconds
 * @param position on its medium, from 1
 */
public record QueueEntry(
        UUID entryId,
        UUID trackId,
        String trackName,
        int duration,
        int position,
        UUID artistId,
        String artistName,
        UUID releaseId,
        String releaseName) {}
