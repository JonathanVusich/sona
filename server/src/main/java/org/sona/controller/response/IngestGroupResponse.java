package org.sona.controller.response;

import java.util.List;
import java.util.UUID;
import org.sona.model.enums.IngestGroupKind;

/**
 * A group of files to upload, under the ID Sona gave it.
 *
 * @param tracks the group's files, in the order the uploader listed them, each under the ID it is uploaded to
 */
public record IngestGroupResponse(
        UUID groupIngestId, IngestGroupKind kind, String name, List<TrackIngestResponse> tracks) {}
