package org.sona.db;

import java.util.UUID;

/**
 * The release a track is on, as a link to its page.
 */
public record TrackRelease(UUID releaseId, String name) {}
