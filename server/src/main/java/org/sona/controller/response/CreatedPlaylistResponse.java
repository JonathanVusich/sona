package org.sona.controller.response;

import java.util.UUID;

/**
 * @param playlistId the ID Sona gave the new playlist
 */
public record CreatedPlaylistResponse(UUID playlistId) {}
