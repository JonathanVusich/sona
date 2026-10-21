package org.sona.db;

import java.util.List;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Release;

/**
 * A release, read in one query with its artist, its labels by name, and its media in order, each with its tracks in
 * order and each track with its own artist.
 */
public record ReleaseDetails(Release release, Artist artist, List<Label> labels, List<MediumWithTracks> media) {}
