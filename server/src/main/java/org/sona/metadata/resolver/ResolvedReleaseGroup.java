package org.sona.metadata.resolver;

import java.time.LocalDate;
import java.util.UUID;
import org.sona.model.enums.ReleaseGroupType;

/**
 * @param type             null when MusicBrainz gives the release group no primary type
 * @param firstReleaseDate the date of the group's earliest release anywhere, with the parts MusicBrainz doesn't know
 *                         as the first, or null
 */
public record ResolvedReleaseGroup(
        UUID releaseGroupId, String title, ReleaseGroupType type, LocalDate firstReleaseDate, ResolvedArtist artist) {}
