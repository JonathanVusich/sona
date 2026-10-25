package org.sona.controller.response;

import java.util.List;
import java.util.UUID;

/**
 * Everything the playlist page shows.
 *
 * @param description null when the user gave it none
 * @param tracks      every track on the playlist, in the user's order
 */
public record PlaylistResponse(UUID playlistId, String name, String description, List<PlaylistTrackResponse> tracks) {}
