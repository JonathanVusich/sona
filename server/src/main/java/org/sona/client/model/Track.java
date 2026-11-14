package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * A track on a medium of a release.
 *
 * @param position  on its medium, from 1; lookups only
 * @param length    in milliseconds; null when MusicBrainz doesn't know it
 * @param recording release lookups that include recordings only
 */
public record Track(
        UUID id,
        String number,
        String title,
        Integer length,
        Integer position,
        Recording recording,
        // Release lookups that include artist credits only.
        @JsonProperty("artist-credit") List<ArtistCredit> artistCredit) {}
