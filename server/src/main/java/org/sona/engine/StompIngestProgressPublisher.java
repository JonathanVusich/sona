package org.sona.engine;

import static org.sona.controller.ApiRoutes.INGEST_PROGRESS;

import lombok.RequiredArgsConstructor;
import org.sona.mappers.TrackIngestResponseMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

/**
 * Sends each state change to everyone subscribed to {@link org.sona.controller.ApiRoutes#INGEST_PROGRESS}, which
 * needs {@code library:upload}: storage is shared, so every uploader sees every ingest.
 */
@Service
@RequiredArgsConstructor
public final class StompIngestProgressPublisher implements IngestProgressPublisher {

    private final SimpMessageSendingOperations messaging;
    private final TrackIngestResponseMapper mapper;

    @Override
    public void publish(final TrackToIngest ingest, final IngestState state) {
        messaging.convertAndSend(INGEST_PROGRESS, mapper.toResponse(ingest, state));
    }
}
