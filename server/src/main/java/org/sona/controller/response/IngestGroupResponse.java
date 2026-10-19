package org.sona.controller.response;

import java.util.UUID;

/**
 * An upload just started.
 *
 * @param groupIngestId the ID to upload each of its files under
 */
public record IngestGroupResponse(UUID groupIngestId) {
}
