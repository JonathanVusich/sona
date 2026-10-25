package org.sona.controller.response;

import java.util.UUID;

/**
 * The release a track is on, as lists of tracks from many releases name and link it.
 */
public record TrackReleaseResponse(UUID releaseId, String name) {}
