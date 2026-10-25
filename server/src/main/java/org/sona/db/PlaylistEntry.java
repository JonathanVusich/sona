package org.sona.db;

import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.Track;

/**
 * A track on a playlist, with its own artist and the release it's on.
 */
public record PlaylistEntry(Track track, Artist artist, Release release) {}
