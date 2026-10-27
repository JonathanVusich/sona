package org.sona.db;

import java.util.List;
import java.util.UUID;

/**
 * A playlist as the list of a user's playlists shows it.
 *
 * @param coverNames the names of the release groups its first tracks are from, up to four, for its cover
 */
public record PlaylistSummary(UUID playlistId, String name, int trackCount, List<String> coverNames) {}
