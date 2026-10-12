package org.sona.client;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.sona.client.model.Artist;
import org.sona.client.model.EntityType;
import org.sona.client.model.Release;
import org.sona.client.model.query.RecordingQuery;
import org.sona.client.model.response.TrackResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.USER_AGENT;

@Service
@RequiredArgsConstructor
public class MusicBrainzClient {

    private static final String USER_AGENT_VALUE = "Sona/0.1.0 ( jonathan@vusich.cloud )";

    private final RestClient restClient;

    // MusicBrainz allows about one request a second per client; see resilience4j.ratelimiter.instances.musicbrainz.
    @RateLimiter(name = "musicbrainz")
    public TrackResponse searchRecordings(RecordingQuery query) {
        return restClient.get()
                .uri(uriBuilder -> formatTrackQuery(uriBuilder, query))
                .header(USER_AGENT, USER_AGENT_VALUE)
                .retrieve()
                .body(TrackResponse.class);
    }

    /**
     * @return the release with its artists, labels, release group (with its artists) and media
     */
    @RateLimiter(name = "musicbrainz")
    public Release lookupRelease(final UUID releaseId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(EntityType.RELEASE.getPath() + "/{id}")
                        .queryParam("inc", "{inc}")
                        .queryParam("fmt", "json")
                        .build(releaseId, "artist-credits labels release-groups media"))
                .header(USER_AGENT, USER_AGENT_VALUE)
                .retrieve()
                .body(Release.class);
    }

    @RateLimiter(name = "musicbrainz")
    public Artist lookupArtist(final UUID artistId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(EntityType.ARTIST.getPath() + "/{id}")
                        .queryParam("fmt", "json")
                        .build(artistId))
                .header(USER_AGENT, USER_AGENT_VALUE)
                .retrieve()
                .body(Artist.class);
    }

    private URI formatTrackQuery(final UriBuilder uriBuilder, final RecordingQuery query) {
        // Pass the query as a URI variable so it is fully encoded ('&', '+', '{' etc. in titles).
        return uriBuilder
                .path(EntityType.RECORDING.getPath())
                .queryParam("query", "{query}")
                .queryParam("fmt", "json")
                .build(query.toQueryString());
    }
}
