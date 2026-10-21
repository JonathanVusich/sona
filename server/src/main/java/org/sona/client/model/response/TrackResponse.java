package org.sona.client.model.response;

import java.time.Instant;
import java.util.List;
import org.sona.client.model.Recording;

public record TrackResponse(Instant created, Integer count, Integer offset, List<Recording> recordings) {}
