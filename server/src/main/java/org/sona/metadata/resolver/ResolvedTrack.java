package org.sona.metadata.resolver;

import java.util.UUID;

/**
 * A track matched to a MusicBrainz recording on one of its releases. The release's own details come from
 * {@link MetadataResolver#resolveRelease}.
 *
 * @param artist      who the recording is credited to; search results leave out their country
 * @param discNumber  the release's medium the track is on, from 1
 * @param trackNumber the track's position on that medium, from 1
 */
public record ResolvedTrack(
        UUID recordingId,
        String title,
        ResolvedArtist artist,
        UUID releaseId,
        int discNumber,
        int trackNumber
) {
}
