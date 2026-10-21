package org.sona.controller.response;

import org.sona.model.enums.IngestState;

import java.util.UUID;

/**
 * One uploaded file, and how far its ingest has got.
 *
 * @param originalFileName the file's name where it was uploaded from
 */
public record TrackIngestResponse(
        UUID trackIngestId,
        UUID groupIngestId,
        String originalFileName,
        IngestState state
) {
}
