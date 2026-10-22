package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record ReleaseGroup(
        UUID id,
        String title,
        // Album, Single, EP, Broadcast or Other; null when MusicBrainz has none.
        @JsonProperty("primary-type") String primaryType,
        // The date of the group's earliest release, as precise as MusicBrainz knows it; lookups only.
        @JsonProperty("first-release-date") String firstReleaseDate,
        // Only in lookups that include artist credits.
        @JsonProperty("artist-credit") List<ArtistCredit> artistCredit,
        @JsonProperty("secondary-types") List<String> secondaryTypes,
        @JsonProperty("secondary-type-ids") List<UUID> secondaryTypeIds) {}
