package org.sona.controller.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * @param trackIds the tracks to add to the end of the playlist, in order; any already on it, given twice, or not in
 *                 the library are skipped
 */
public record AddTracksRequest(
        @NotEmpty @Size(max = PlaylistLimits.MAX_TRACKS_ADDED)
        List<@NotNull UUID> trackIds) {}
