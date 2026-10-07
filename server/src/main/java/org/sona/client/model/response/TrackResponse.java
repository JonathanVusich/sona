package org.sona.client.model.response;

import org.sona.client.model.Recording;

import java.time.Instant;
import java.util.List;

public record TrackResponse(
        Instant created,
        Integer count,
        Integer offset,
        List<Recording> recordings
) {
}
