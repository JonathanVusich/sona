package org.sona.db;

import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Track;

/**
 * A track with its own artist, who may not be the release's (as on a compilation).
 */
public record TrackWithArtist(Track track, Artist artist) {}
