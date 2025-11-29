package org.sona.format;

import lombok.Builder;
import org.sona.utils.CollectionUtils;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Builder
public record TrackMetadata(
        String track,
        List<String> artists,
        String release,
        Year releaseYear,
        int trackNumber,
        int duration, // in seconds
        UUID mbTrack,
        UUID mbReleaseTrack,
        UUID mbRelease,
        UUID mbReleaseGroup,
        List<UUID> mbArtists,
        List<UUID> mbReleaseArtists
) {
    public boolean hasMbMetadata() {
        return mbTrack == null
                && mbReleaseTrack == null
                && mbRelease == null
                && mbReleaseGroup == null
                && CollectionUtils.empty(mbArtists)
                && CollectionUtils.empty(mbReleaseArtists);
    }
}
