package org.sona.exception;

/**
 * A request to start the queue at a track that its source doesn't have.
 */
public final class QueueStartOutOfRangeException extends SonaException {

    public QueueStartOutOfRangeException(final int startIndex) {
        super("There is no track at index %d to start the queue with".formatted(startIndex));
    }
}
