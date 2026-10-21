package org.sona.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.sona.model.enums.IngestGroupKind;

/**
 * A group of files to upload, once the uploader has settled which files belong together.
 *
 * @param kind {@code ALBUM} for files that belong together, such as one folder, or {@code UNKNOWN} for loose files,
 *             which matching groups itself
 * @param name the folder's name, or what the uploader called the group
 */
public record IngestGroupRequest(@NotNull IngestGroupKind kind, @NotBlank String name) {
}
