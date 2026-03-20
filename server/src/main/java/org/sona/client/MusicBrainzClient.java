package org.sona.client;

import lombok.RequiredArgsConstructor;
import org.sona.client.model.EntityType;
import org.sona.client.model.response.TrackResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriBuilderFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.springframework.http.HttpHeaders.USER_AGENT;

@Service
@RequiredArgsConstructor
public final class MusicBrainzClient {

    private final UriBuilderFactory uriFactory;
    private final HttpClient httpClient;
    private final JsonMapper mapper;

    public TrackResponse findTrack(String trackName) throws IOException, InterruptedException {
        final var trackUri = formatTrackQuery(trackName);

        final var httpRequest = get()
                .uri(trackUri)
                .build();

        final var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(response.body(), TrackResponse.class);
    }

    private URI formatTrackQuery(final String trackName) {
        final String queryBuilder = "\"" + trackName.toLowerCase() + "\"";
        return uriFactory.builder()
                .path(EntityType.RECORDING.getPath())
                .queryParam("query", queryBuilder)
                .queryParam("fmt", "json")
                .build();
    }

    private HttpRequest.Builder get() {
        return HttpRequest.newBuilder()
                .header(USER_AGENT, "Sona/0.1.0 ( jonathan@vusich.cloud )")
                .GET();
    }
}
