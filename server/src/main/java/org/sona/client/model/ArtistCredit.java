package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record ArtistCredit(
        UUID id,
        String name,
        @JsonProperty("sort-name")
        String sortName,
        String disambiguation,
        List<ArtistAlias> aliases
) {
}
