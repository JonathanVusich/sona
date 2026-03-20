package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record Recording(
        UUID id,
        String title,

        @JsonProperty("artist-credit-id")
        UUID artistCreditId,
        @JsonProperty("artist-credit")
        List<ArtistCredit> artistCredit,
        List<Release> releases,
        int score
) {
}
