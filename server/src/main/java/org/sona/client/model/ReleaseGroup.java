package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record ReleaseGroup(
        UUID id,
        String title,
        @JsonProperty("secondary-types")
        List<String> secondaryTypes,
        @JsonProperty("secondary-type-ids")
        List<UUID> secondaryTypeIds
) {
}
