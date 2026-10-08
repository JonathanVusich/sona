package org.sona.metadata.resolver;

import java.util.UUID;

/**
 * A track matched to MusicBrainz: the recording and its artist, plus the release (with its artist and release group)
 * it was matched on.
 */
public record ResolvedTrack(
        UUID recordingId,
        String title,
        ResolvedArtist artist,
        UUID releaseId,
        String releaseTitle,
        ResolvedArtist releaseArtist,
        UUID releaseGroupId,
        String releaseGroupTitle
) {
}
