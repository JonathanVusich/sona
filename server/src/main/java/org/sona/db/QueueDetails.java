package org.sona.db;

import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;

/**
 * A user's queue, read in one query without its entries, which are read a page at a time.
 *
 * @param version         goes up with every change, from 0 for a queue the user has never changed
 * @param currentEntry    null when the queue is empty
 * @param positionSeconds how far into the current entry the user last reported being
 */
public record QueueDetails(
        long version, QueueEntry currentEntry, int positionSeconds, ShuffleMode shuffle, RepeatMode repeat) {

    /**
     * @return the queue of a user who has never changed theirs
     */
    public static QueueDetails empty() {
        return new QueueDetails(0, null, 0, ShuffleMode.OFF, RepeatMode.OFF);
    }
}
