package org.sona.metadata.resolver;

import java.util.List;

/**
 * What resolving a track came to: a match sure enough to import, or candidates for a person to choose between.
 */
public sealed interface TrackResolution {

    record Matched(ResolvedTrack track) implements TrackResolution {}

    /**
     * @param candidates the best first; empty when nothing was found
     */
    record NeedsReview(List<TrackCandidate> candidates) implements TrackResolution {}
}
