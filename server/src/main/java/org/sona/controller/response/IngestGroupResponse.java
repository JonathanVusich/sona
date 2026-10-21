package org.sona.controller.response;

import java.util.UUID;

/**
 * A group of uploaded files just started, which the uploader says belong together, such as one album.
 *
 * @param groupIngestId the ID to upload each of its files under
 */
public record IngestGroupResponse(UUID groupIngestId) {
}
