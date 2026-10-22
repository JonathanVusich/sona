package org.sona.db;

import java.util.List;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;

/**
 * An artist, read in one query with their release groups, newest first.
 */
public record ArtistDetails(Artist artist, List<ReleaseGroup> releaseGroups) {}
