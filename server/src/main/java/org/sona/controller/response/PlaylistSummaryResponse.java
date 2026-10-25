package org.sona.controller.response;

import java.util.List;
import java.util.UUID;

/**
 * A playlist as the list of the user's playlists shows it.
 *
 * @param coverNames the names of the release groups its first tracks are from, in the order they first appear, up
 *                   to four, for its cover; empty for a playlist with no tracks
 */
public record PlaylistSummaryResponse(UUID playlistId, String name, int trackCount, List<String> coverNames) {}
