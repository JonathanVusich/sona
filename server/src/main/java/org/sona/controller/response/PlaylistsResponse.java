package org.sona.controller.response;

import java.util.List;

/**
 * @param playlists every playlist the user has, by name
 */
public record PlaylistsResponse(List<PlaylistSummaryResponse> playlists) {}
