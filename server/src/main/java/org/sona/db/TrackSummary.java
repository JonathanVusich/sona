package org.sona.db;

import java.util.UUID;

/**
 * A track as lists of tracks show it: with its own artist, and the release it's on.
 *
 * @param duration in seconds
 * @param position on its medium, from 1
 */
public record TrackSummary(
        UUID trackId, String name, int duration, int position, TrackArtist artist, TrackRelease release) {}
