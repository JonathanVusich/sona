package org.sona.db;

import java.util.List;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;

/**
 * A release group, read in one query with its artist, its default release's tracks, and its editions, earliest first.
 */
public record ReleaseGroupDetails(
        ReleaseGroup releaseGroup, Artist artist, DefaultRelease defaultRelease, List<Edition> editions) {}
