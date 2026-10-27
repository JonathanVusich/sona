package org.sona.controller.response;

import java.util.List;

/**
 * A page of the queue's entries, in the order they play.
 *
 * @param next what to pass as the next request's cursor, or null when there are no more entries that way
 */
public record QueueEntryPageResponse(List<QueueEntryResponse> entries, Integer next) {}
