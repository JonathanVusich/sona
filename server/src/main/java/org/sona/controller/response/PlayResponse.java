package org.sona.controller.response;

import java.util.UUID;

/**
 * One time the user played a track.
 */
public record PlayResponse(UUID playId, PlaylistTrackResponse track) {}
