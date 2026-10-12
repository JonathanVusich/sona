package org.sona.db;

import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;

/**
 * A release group, read in one query with its artist, which is null if it has none.
 */
public record ReleaseGroupWithArtist(ReleaseGroup releaseGroup, Artist artist) {
}
