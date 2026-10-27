package org.sona.controller.response;

import java.util.UUID;

/**
 * @param entryId the entry's own ID, as a track can be in the queue more than once
 */
public record QueueEntryResponse(UUID entryId, PlayedTrackResponse track) {}
