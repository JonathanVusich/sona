package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record Release(
        UUID id,
        @JsonProperty("status-id") UUID statusId,
        @JsonProperty("artist-credit-id") UUID artistCreditId,
        // Search results only.
        Integer count,
        String title,
        String status,
        @JsonProperty("artist-credit") List<ArtistCredit> artistCredit,
        @JsonProperty("release-group") ReleaseGroup releaseGroup,
        // Search results only.
        @JsonProperty("track-count") Integer trackCount,
        List<Media> media,
        // As precise as MusicBrainz knows it: "2002", "2002-08" or "2002-08-26".
        String date,
        // ISO 3166-1 code, or a MusicBrainz code like "XW" for worldwide.
        String country,
        // Only in lookups that include labels.
        @JsonProperty("label-info") List<LabelInfo> labelInfo) {}
