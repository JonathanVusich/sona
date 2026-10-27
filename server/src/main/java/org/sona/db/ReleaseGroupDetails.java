package org.sona.db;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.sona.model.enums.ReleaseGroupType;

/**
 * A release group, read in one query with its artist, its default release's tracks, and its editions, earliest first.
 *
 * @param type             null when MusicBrainz gives the release group no primary type
 * @param firstReleaseDate null when unknown
 */
public record ReleaseGroupDetails(
        UUID releaseGroupId,
        String name,
        ReleaseGroupType type,
        LocalDate firstReleaseDate,
        UUID artistId,
        String artistName,
        DefaultRelease defaultRelease,
        List<Edition> editions) {}
