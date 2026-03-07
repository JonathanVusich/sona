package org.sona.metadata;

import java.util.List;
import java.util.UUID;

public record MusicBrainzMetadata(
        // MusicBrainz metadata
        UUID mbTrack,
        String mbReleaseTrackName,
        UUID mbReleaseTrack,
        String mbReleaseName,
        UUID mbRelease,
        String mbReleaseGroupName,
        UUID mbReleaseGroup,
        List<UUID> mbArtists,
        List<UUID> mbReleaseArtists
) { }
