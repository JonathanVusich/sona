package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ArtistAlias(
        @JsonProperty("sort-name")
        String sortName,
        @JsonProperty("type-id")
        UUID typeId,
        String name,
        String locale,
        String type,
        boolean primary,
        @JsonProperty("begin-date")
        Instant beginDate,
        @JsonProperty("end-date")
        Instant endDate
) {
}
