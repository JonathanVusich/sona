package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record Artist(
        UUID id,
        String name,
        @JsonProperty("sort-name")
        String sortName,
        String disambiguation
) {
}
