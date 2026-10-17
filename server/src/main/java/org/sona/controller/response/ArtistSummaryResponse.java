package org.sona.controller.response;

import java.util.UUID;

/**
 * An artist as the list of artists shows them.
 *
 * @param releaseGroupCount how many of the artist's release groups are in the library
 */
public record ArtistSummaryResponse(UUID artistId, String name, int releaseGroupCount) {
}
