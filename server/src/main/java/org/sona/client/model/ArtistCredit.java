package org.sona.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * One artist in a recording's or release's credit. The name is how the artist is credited here, which can differ from
 * their own name. The join phrase follows the name and links it to the next artist, like " & ". MusicBrainz leaves it
 * out when it is empty.
 */
public record ArtistCredit(
        String name,
        @JsonProperty("joinphrase")
        String joinPhrase,
        Artist artist
) {

    public ArtistCredit {
        joinPhrase = Objects.requireNonNullElse(joinPhrase, "");
    }
}
