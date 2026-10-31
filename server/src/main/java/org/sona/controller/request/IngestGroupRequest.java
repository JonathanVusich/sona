package org.sona.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.sona.model.enums.IngestGroupKind;

/**
 * A group of files to upload, once the uploader has settled which files belong together.
 *
 * @param kind {@code ALBUM} for files that belong together, such as one folder, or {@code UNKNOWN} for loose files,
 *             which matching groups itself
 * @param name the folder's name, or what the uploader called the group
 * @param filenames the names of all the files the uploader will send, whose extensions say their formats; matching
 *                  waits until all of them are uploaded
 */
public record IngestGroupRequest(
        @NotNull IngestGroupKind kind,
        @NotBlank String name,
        @NotEmpty List<@NotBlank String> filenames) {}
