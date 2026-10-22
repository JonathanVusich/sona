package org.sona.controller.response;

import java.util.UUID;
import org.sona.model.enums.IngestGroupKind;

/**
 * A group of files to upload, under the ID Sona gave it, which its files are uploaded under.
 */
public record IngestGroupResponse(UUID groupIngestId, IngestGroupKind kind, String name) {}
