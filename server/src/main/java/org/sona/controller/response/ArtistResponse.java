package org.sona.controller.response;

import java.util.UUID;

/**
 * @param country ISO 3166-1 code of the artist's area, or null when MusicBrainz doesn't know it
 */
public record ArtistResponse(UUID artistId, String name, String country) {
}
