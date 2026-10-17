package org.sona.db;

import org.sona.model.tables.pojos.Release;

import java.util.List;

/**
 * The release that stands for its group, as the release group page shows its tracks: the group's earliest release,
 * so its original rather than a later deluxe or instrumental edition, with its media in order.
 */
public record DefaultRelease(Release release, List<MediumWithTracks> media) {
}
