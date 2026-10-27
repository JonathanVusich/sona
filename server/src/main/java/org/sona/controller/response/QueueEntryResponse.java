package org.sona.controller.response;

import java.util.UUID;

/**
 * @param entryId   the entry's own ID, as a track can be in the queue more than once
 * @param playOrder where the entry plays in the queue as it is now, shuffled or not: the cursor for the pages around
 *                  it. It grows from one entry to the next, but not by one, and a client that sees the queue's
 *                  shuffle change reads its entries again
 */
public record QueueEntryResponse(UUID entryId, int playOrder, PlayedTrackResponse track) {}
