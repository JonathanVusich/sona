package org.sona.db;

import java.util.UUID;

/**
 * The artist a track names, as a link to their page.
 */
public record TrackArtist(UUID artistId, String name) {}
