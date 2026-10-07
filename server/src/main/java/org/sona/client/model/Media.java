package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record Media(
        UUID id,
        int position,
        List<Track> track,
        @JsonProperty("track-count")
        int trackCount,
        @JsonProperty("track-offset")
        int trackOffset
) {
}
