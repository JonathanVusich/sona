package org.sona.engine;

import static org.sona.controller.ApiRoutes.INGEST_PROGRESS_QUEUE;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.mappers.TrackIngestResponseMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

/**
 * Sends each state change to the user who uploaded the ingest's group, on every connection they have subscribed to
 * {@link org.sona.controller.ApiRoutes#INGEST_PROGRESS}.
 */
@Service
@RequiredArgsConstructor
public final class StompIngestProgressPublisher implements IngestProgressPublisher {

    private final SimpMessageSendingOperations messaging;
    private final TrackIngestResponseMapper mapper;

    @Override
    public void publish(final UUID uploaderId, final TrackToIngest ingest, final IngestState state) {
        messaging.convertAndSendToUser(uploaderId.toString(), INGEST_PROGRESS_QUEUE, mapper.toResponse(ingest, state));
    }
}
