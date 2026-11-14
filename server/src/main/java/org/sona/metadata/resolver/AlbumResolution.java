package org.sona.metadata.resolver;

import java.util.List;

/**
 * What resolving an album's files together came to: every file placed on a track of one release, or releases for a
 * person to choose between.
 */
public sealed interface AlbumResolution {

    /**
     * @param tracks each file's track on the release, in the order the files were given
     */
    record Matched(List<ResolvedTrack> tracks) implements AlbumResolution {}

    /**
     * @param releases the best first; empty when no release has as many tracks as the album has files
     */
    record NeedsReview(List<ReleaseCandidate> releases) implements AlbumResolution {}
}
