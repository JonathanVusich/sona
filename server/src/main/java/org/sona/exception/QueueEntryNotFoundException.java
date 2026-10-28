package org.sona.exception;

import java.util.UUID;

/**
 * A queue entry that doesn't exist, or is in someone else's queue.
 */
public final class QueueEntryNotFoundException extends SonaException {

    public QueueEntryNotFoundException(final UUID entryId) {
        super("Queue entry %s isn't in the user's queue".formatted(entryId));
    }
}
