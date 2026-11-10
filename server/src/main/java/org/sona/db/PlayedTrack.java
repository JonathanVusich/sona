package org.sona.db;

import java.util.UUID;

/**
 * A time a user played a track, with just what a list of plays shows.
 */
public record PlayedTrack(UUID playId, TrackSummary track) {}
