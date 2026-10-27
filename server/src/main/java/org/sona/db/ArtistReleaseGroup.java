package org.sona.db;

import java.time.LocalDate;
import java.util.UUID;
import org.sona.model.enums.ReleaseGroupType;

/**
 * One of an artist's release groups, as the artist page lists them.
 *
 * @param type             null when MusicBrainz gives the release group no primary type
 * @param firstReleaseDate null when unknown
 */
public record ArtistReleaseGroup(UUID releaseGroupId, String name, ReleaseGroupType type, LocalDate firstReleaseDate) {}
