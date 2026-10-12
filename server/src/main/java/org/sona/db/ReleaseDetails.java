package org.sona.db;

import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;

import java.util.List;

/**
 * A release, read in one query with its artist, its labels by name, its media in order and its tracks in disc and
 * track order, each with its own artist.
 */
public record ReleaseDetails(Release release, Artist artist, List<Label> labels, List<Medium> media,
                             List<TrackWithArtist> tracks) {
}
