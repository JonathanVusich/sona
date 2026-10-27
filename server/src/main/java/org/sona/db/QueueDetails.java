package org.sona.db;

import java.util.List;
import java.util.UUID;
import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;

/**
 * A user's queue, read in one query with its entries in play order.
 *
 * @param version         goes up with every change, from 0 for a queue the user has never changed
 * @param currentEntryId  null when the queue is empty
 * @param positionSeconds how far into the current entry the user last reported being
 */
public record QueueDetails(
        long version,
        UUID currentEntryId,
        int positionSeconds,
        ShuffleMode shuffle,
        RepeatMode repeat,
        List<QueueEntry> entries) {

    /**
     * @return the queue of a user who has never changed theirs
     */
    public static QueueDetails empty() {
        return new QueueDetails(0, null, 0, ShuffleMode.OFF, RepeatMode.OFF, List.of());
    }
}
