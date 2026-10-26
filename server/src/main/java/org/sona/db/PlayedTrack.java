package org.sona.db;

import org.sona.model.tables.pojos.Play;

/**
 * A time a user played a track, with the track, its artist and its release.
 */
public record PlayedTrack(Play play, PlaylistEntry entry) {}
