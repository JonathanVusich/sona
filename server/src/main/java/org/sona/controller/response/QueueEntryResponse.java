package org.sona.controller.response;

import java.util.UUID;

/**
 * @param entryId   the entry's own ID, as a track can be in the queue more than once
 * @param playOrder where the entry plays in the queue, from 1: the cursor for the pages around it
 */
public record QueueEntryResponse(UUID entryId, int playOrder, PlayedTrackResponse track) {}
