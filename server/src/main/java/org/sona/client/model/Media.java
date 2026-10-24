package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record Media(
        UUID id,
        int position,
        // Like "CD" or "Digital Media"; null when MusicBrainz doesn't know.
        String format,
        List<Track> track,
        @JsonProperty("track-count") int trackCount,
        // Recording search results and lookups only: how many of the medium's tracks come before those listed.
        @JsonProperty("track-offset") Integer trackOffset) {}
