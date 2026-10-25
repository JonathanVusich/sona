package org.sona.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * @param description null for none
 * @param trackIds    the tracks to start it with, in order, which may be none; any given twice, or not in the library,
 *                    are skipped
 */
public record CreatePlaylistRequest(
        @NotBlank String name,
        String description,

        @NotNull @Size(max = PlaylistLimits.MAX_TRACKS_ADDED)
        List<@NotNull UUID> trackIds) {}
