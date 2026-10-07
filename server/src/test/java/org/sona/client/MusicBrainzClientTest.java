package org.sona.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sona.client.model.query.RecordingQuery;
import org.sona.config.MusicBrainzConfig;
import org.sona.config.properties.MusicBrainzProperties;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MusicBrainzClientTest {

    private static final String RECORDING_PATH = "/ws/2/recording";

    // Response bodies are recorded from the live API into src/test/resources/wiremock/__files.
    @RegisterExtension
    static final WireMockExtension musicBrainz = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"))
            .build();

    private final MusicBrainzClient client = client(musicBrainz.baseUrl() + "/ws/2/");

    @Test
    void searchRecordings() {
        final var query = new RecordingQuery("A Whisper", "Coldplay", 9, -1);
        musicBrainz.stubFor(get(urlPathEqualTo(RECORDING_PATH))
                .withQueryParam("query", equalTo(query.build()))
                .withQueryParam("fmt", equalTo("json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("recording/a-whisper.json")));

        final var response = client.searchRecordings(query);

        assertThat(response.count()).isEqualTo(3);
        final var recording = response.recordings().getFirst();
        assertThat(recording.id()).isEqualTo(UUID.fromString("53616964-f51e-4ce0-a589-a5581f771197"));
        assertThat(recording.title()).isEqualTo("A Whisper");
        assertThat(recording.score()).isEqualTo(100);

        final var credit = recording.artistCredit().getFirst();
        assertThat(credit.name()).isEqualTo("Coldplay");

        final var release = recording.releases().getFirst();
        assertThat(release.title()).isEqualTo("A Rush of Blood to the Head");
        assertThat(release.releaseGroup().id()).isEqualTo(UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf"));
    }

    @Test
    void sendQueryWithReservedCharactersIntact() {
        final var query = new RecordingQuery("Rock & Roll + {Live} 100%", "Simon & Garfunkel", -1, -1);
        musicBrainz.stubFor(get(urlPathEqualTo(RECORDING_PATH)).willReturn(notFound()));

        assertThatThrownBy(() -> client.searchRecordings(query)).isInstanceOf(HttpClientErrorException.NotFound.class);
        musicBrainz.verify(getRequestedFor(urlPathEqualTo(RECORDING_PATH))
                .withQueryParam("query", equalTo(query.build())));
    }

    @Test
    void failOnErrorStatus() {
        musicBrainz.stubFor(get(urlPathEqualTo(RECORDING_PATH)).willReturn(serviceUnavailable()));

        assertThatThrownBy(() -> client.searchRecordings(new RecordingQuery("A Whisper", null, -1, -1)))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
    }

    // Built without Spring, so the @RateLimiter aspect doesn't apply.
    private static MusicBrainzClient client(final String url) {
        final var config = new MusicBrainzConfig();
        final var mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        final var restClient = config.musicBrainzRestClient(config.uriBuilderFactory(new MusicBrainzProperties(url)), mapper);
        return new MusicBrainzClient(restClient);
    }
}
