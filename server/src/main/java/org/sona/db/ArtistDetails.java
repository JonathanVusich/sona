package org.sona.db;

import java.util.List;
import java.util.UUID;

/**
 * An artist, read in one query with their release groups, newest first.
 *
 * @param country null when MusicBrainz doesn't know it
 */
public record ArtistDetails(UUID artistId, String name, String country, List<ArtistReleaseGroup> releaseGroups) {}
