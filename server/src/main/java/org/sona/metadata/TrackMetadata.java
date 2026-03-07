package org.sona.metadata;

import lombok.Builder;

import java.time.Year;
import java.util.List;
import java.util.Map;

@Builder
public record TrackMetadata(
        String trackTitle,
        List<String> artists,
        String release,
        Year releaseYear,
        int trackNumber,
        int duration, // in seconds

        // Metadata tags
        Map<String, String> tags
) {
}
