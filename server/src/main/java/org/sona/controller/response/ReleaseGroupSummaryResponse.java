package org.sona.controller.response;

import java.util.UUID;

/**
 * A release group as the list of albums shows it.
 */
public record ReleaseGroupSummaryResponse(UUID releaseGroupId, String name, ArtistResponse artist) {
}
