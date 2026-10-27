package org.sona.db;

import java.util.List;
import java.util.UUID;

/**
 * The release that stands for its group, as the release group page shows its tracks: the group's earliest release,
 * so its original rather than a later deluxe or instrumental edition, with its media in order.
 */
public record DefaultRelease(UUID releaseId, List<MediumWithTracks> media) {}
