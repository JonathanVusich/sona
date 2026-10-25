package org.sona.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * A playlist's new name and description.
 *
 * @param description null for none
 */
public record PlaylistDetailsRequest(@NotBlank String name, String description) {}
