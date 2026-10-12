package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A label a release came out on. MusicBrainz leaves out the label when only the catalog number is known.
 */
public record LabelInfo(
        @JsonProperty("catalog-number")
        String catalogNumber,
        Label label
) {
}
