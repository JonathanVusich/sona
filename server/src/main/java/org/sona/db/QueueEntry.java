package org.sona.db;

import java.util.UUID;

/**
 * A track in a user's queue, with just what the queue shows.
 *
 * @param playOrder where the entry plays in the queue as it is now, shuffled or not: it grows from one entry to the
 *                  next, but not by one, and no two entries of a queue share one
 */
public record QueueEntry(UUID entryId, int playOrder, TrackSummary track) {}
