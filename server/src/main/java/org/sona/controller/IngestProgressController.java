package org.sona.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.db.TrackToIngestDao;
import org.sona.mappers.TrackIngestResponseMapper;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * Lets a client catch up on ingests over its STOMP connection: subscribed to {@link ApiRoutes#INGEST_PROGRESS} first,
 * it then subscribes to {@link ApiRoutes#UNFINISHED_INGESTS} for the state each ingest had by then. A change published
 * in between can arrive before that answer, so the client should keep the state it got from the topic. Subscribing
 * needs {@code library:upload}, which {@code SocketAccessInterceptor} checks.
 */
@Controller
@RequiredArgsConstructor
public class IngestProgressController {

    private final TrackToIngestDao trackToIngestDao;
    private final TrackIngestResponseMapper mapper;

    @SubscribeMapping(ApiRoutes.UNFINISHED_INGESTS_MAPPING)
    public List<TrackIngestResponse> unfinishedIngests() {
        return trackToIngestDao.loadUnfinished().stream()
                .map(mapper::toResponse)
                .toList();
    }
}
