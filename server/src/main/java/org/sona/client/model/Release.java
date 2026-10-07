package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record Release(
        UUID id,
        @JsonProperty("status-id")
        UUID statusId,
        @JsonProperty("artist-credit-id")
        UUID artistCreditId,
        int count,
        String title,
        String status,
        @JsonProperty("artist-credit")
        List<ReleaseCredit> artistCredit,
        @JsonProperty("release-group")
        ReleaseGroup releaseGroup,
        @JsonProperty("track-count")
        int trackCount,
        List<Media> media
) {
}
