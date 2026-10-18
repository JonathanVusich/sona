package org.sona.db;

import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;

import java.util.List;

/**
 * A release, read in one query with its artist, its labels by name, and its media in order, each with its tracks in
 * order and each track with its own artist.
 *
 * @param allMedia every medium of the release, for its formats
 * @param media    the media with a track in the library, each with those tracks
 */
public record ReleaseDetails(Release release, Artist artist, List<Label> labels, List<Medium> allMedia,
                             List<MediumWithTracks> media) {
}
