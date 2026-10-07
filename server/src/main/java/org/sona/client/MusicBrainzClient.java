package org.sona.client;

import lombok.RequiredArgsConstructor;
import org.sona.client.model.EntityType;
import org.sona.client.model.query.RecordingQuery;
import org.sona.client.model.response.TrackResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;

import static org.springframework.http.HttpHeaders.USER_AGENT;

@Service
@RequiredArgsConstructor
public final class MusicBrainzClient {

    private final RestClient restClient;

    public TrackResponse searchRecordings(RecordingQuery query) {
        return restClient.get()
                .uri(uriBuilder -> formatTrackQuery(uriBuilder, query))
                .header(USER_AGENT, "Sona/0.1.0 ( jonathan@vusich.cloud )")
                .retrieve()
                .body(TrackResponse.class);
    }

    private URI formatTrackQuery(final UriBuilder uriBuilder, final RecordingQuery query) {
        // Pass the query as a URI variable so it is fully encoded ('&', '+', '{' etc. in titles).
        return uriBuilder
                .path(EntityType.RECORDING.getPath())
                .queryParam("query", "{query}")
                .queryParam("fmt", "json")
                .build(query.build());
    }
}
