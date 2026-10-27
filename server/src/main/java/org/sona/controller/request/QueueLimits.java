package org.sona.controller.request;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class QueueLimits {

    /**
     * How many entries a queue may hold, enough for a long listening session.
     */
    public static final int MAX_ENTRIES = 1000;
}
