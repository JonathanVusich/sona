package org.sona.controller.response;

import java.util.List;
import java.util.UUID;
import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;

/**
 * The user's play queue.
 *
 * @param version         goes up with every change to the queue, so a client can tell it has missed one
 * @param currentEntryId  null when the queue is empty
 * @param positionSeconds how far into the current entry the user last reported being
 * @param entries         in the order they play, the ones already played included
 */
public record QueueResponse(
        long version,
        UUID currentEntryId,
        int positionSeconds,
        ShuffleMode shuffle,
        RepeatMode repeat,
        List<QueueEntryResponse> entries) {}
