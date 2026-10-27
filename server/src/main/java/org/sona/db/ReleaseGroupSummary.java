package org.sona.db;

import java.time.LocalDate;
import java.util.UUID;
import org.sona.model.enums.ReleaseGroupType;

/**
 * A release group with its artist, as the list of albums shows it.
 *
 * @param type             null when MusicBrainz gives the release group no primary type
 * @param firstReleaseDate null when unknown
 */
public record ReleaseGroupSummary(
        UUID releaseGroupId,
        String name,
        ReleaseGroupType type,
        LocalDate firstReleaseDate,
        UUID artistId,
        String artistName) {}
