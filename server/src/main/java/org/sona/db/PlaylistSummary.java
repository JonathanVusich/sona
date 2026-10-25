package org.sona.db;

import java.util.List;
import org.sona.model.tables.pojos.Playlist;

/**
 * A playlist as the list of a user's playlists shows it.
 *
 * @param coverNames the names of the release groups its first tracks are from, up to four, for its cover
 */
public record PlaylistSummary(Playlist playlist, List<String> coverNames) {}
