package org.sona.controller.request;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class PlaylistLimits {

    /**
     * How many tracks one request may add to a playlist, enough for a box set.
     */
    public static final int MAX_TRACKS_ADDED = 1000;
}
