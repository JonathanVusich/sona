package org.sona.db;

import java.util.List;
import java.util.UUID;

/**
 * A playlist, read in one query with its tracks in order.
 *
 * @param description null when the user gave it none
 */
public record PlaylistDetails(UUID playlistId, String name, String description, List<PlaylistEntry> entries) {}
