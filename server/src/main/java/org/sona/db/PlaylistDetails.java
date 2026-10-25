package org.sona.db;

import java.util.List;
import org.sona.model.tables.pojos.Playlist;

/**
 * A playlist, read in one query with its tracks in order.
 */
public record PlaylistDetails(Playlist playlist, List<PlaylistEntry> entries) {}
