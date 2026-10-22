package org.sona.controller.response;

import java.util.UUID;
import org.sona.model.enums.IngestState;

/**
 * One uploaded file, and how far its ingest has got.
 *
 * @param originalFileName the file's name where it was uploaded from
 */
public record TrackIngestResponse(UUID trackIngestId, UUID groupIngestId, String originalFileName, IngestState state) {}
