package org.sona.db;

import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;

import java.util.List;

/**
 * A release group, read in one query with its artist, its default release's tracks, and its editions, earliest first.
 *
 * @param defaultRelease null when none of the group's releases is stored yet, as when an ingest stopped part-way
 */
public record ReleaseGroupDetails(ReleaseGroup releaseGroup, Artist artist, DefaultRelease defaultRelease,
                                  List<Edition> editions) {
}
