package org.sona.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.client.model.query.RecordingQuery;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The retries are applied by Spring, so they're only tested on the client Spring builds.
 */
@IntegrationTest
class MusicBrainzRetryTest {

    private static final String RECORDING_PATH = "/ws/2/recording";

    @Autowired
    MusicBrainzClient client;

    @Autowired
    WireMockServer musicBrainz;

    private final UUID busyStub = UUID.randomUUID();

    @AfterEach
    void removeBusyStub() {
        // The stub server is shared with other tests.
        musicBrainz.removeStubMapping(musicBrainz.getStubMapping(busyStub).getItem());
        musicBrainz.resetScenarios();
    }

    @Test
    void aSearchMusicBrainzIsTooBusyForIsTriedAgain() {
        final var query = new RecordingQuery("A Whisper", "Coldplay", 9, -1);
        // Busy the first time only; after that the recorded response in wiremock/mappings answers.
        musicBrainz.stubFor(get(urlPathEqualTo(RECORDING_PATH))
                .withId(busyStub)
                .atPriority(1)
                .withQueryParam("query", equalTo(query.toQueryString()))
                .inScenario("busy")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("answered")
                .willReturn(serviceUnavailable()));
        musicBrainz.resetRequests();

        final var response = client.searchRecordings(query);

        assertThat(response.recordings().getFirst().title()).isEqualTo("A Whisper");
        assertThat(musicBrainz.findAll(getRequestedFor(urlPathEqualTo(RECORDING_PATH))))
                .hasSize(2);
    }
}
