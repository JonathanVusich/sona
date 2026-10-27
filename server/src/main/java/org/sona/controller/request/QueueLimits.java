package org.sona.controller.request;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class QueueLimits {

    /**
     * How many tracks a request may list. The queue itself has no limit: a playlist or release of any length fills it
     * from the server's side.
     */
    public static final int MAX_TRACKS = 1000;
}
