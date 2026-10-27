package org.sona.db;

import java.util.UUID;

/**
 * A track as the page of its release lists it, with its own artist, who may not be the release's (as on a
 * compilation).
 *
 * @param duration in seconds
 * @param position on its medium, from 1
 */
public record TrackWithArtist(
        UUID trackId, String name, int duration, int position, UUID artistId, String artistName) {}
