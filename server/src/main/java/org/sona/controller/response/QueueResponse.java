package org.sona.controller.response;

import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;

/**
 * The user's play queue, without its entries, which clients read a page at a time.
 *
 * @param version         goes up with every change to the queue, so a client can tell it has missed one
 * @param currentEntry    null when the queue is empty
 * @param positionSeconds how far into the current entry the user last reported being
 */
public record QueueResponse(
        long version, QueueEntryResponse currentEntry, int positionSeconds, ShuffleMode shuffle, RepeatMode repeat) {}
