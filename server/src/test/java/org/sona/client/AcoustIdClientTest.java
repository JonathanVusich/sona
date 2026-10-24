package org.sona.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sona.client.model.acoustid.LookupResult;
import org.sona.client.model.acoustid.RecordingId;
import org.sona.config.AcoustIdConfig;
import org.sona.config.properties.AcoustIdProperties;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class AcoustIdClientTest {

    private static final String LOOKUP_PATH = "/v2/lookup";

    // Response bodies are recorded from the live API into src/test/resources/wiremock/__files.
    @RegisterExtension
    static final WireMockExtension acoustId = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"))
            .build();

    private final AcoustIdClient client = newClient(acoustId.baseUrl() + "/v2/");

    @Test
    void lookUpTheRecordingsOfAFingerprint() {
        acoustId.stubFor(post(urlPathEqualTo(LOOKUP_PATH))
                .atPriority(1)
                .withFormParam("client", equalTo("test-key"))
                .withFormParam("meta", equalTo("recordingids"))
                .withFormParam("duration", equalTo("641"))
                .withFormParam("fingerprint", equalTo("AQABz0qUkZK4oOfhL-CPc4e5C_wW2H2QH9uDL4cvoT8UNQ"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("acoustid/lower-your-eyelids-to-die-with-the-sun.json")));

        final var response =
                client.lookup("AQABz0qUkZK4oOfhL-CPc4e5C_wW2H2QH9uDL4cvoT8UNQ", Duration.ofSeconds(641, 500_000_000));

        final var recording = List.of(new RecordingId(UUID.fromString("cd2e7c47-16f5-46c6-a37c-a1eb7bf599ff")));
        assertThat(response.results())
                .extracting(LookupResult::id, LookupResult::score, LookupResult::recordings)
                .containsExactly(
                        tuple(UUID.fromString("9ff43b6a-4f16-427c-93c2-92307ca505e0"), 1.0, recording),
                        tuple(UUID.fromString("be6fa248-97d1-4a18-845b-7c6a070b764c"), 1.0, recording));
    }

    @Test
    void readFingerprintsThatMatchNothing() {
        // Served by the stub in src/test/resources/wiremock/mappings, which answers any lookup.
        final var response = client.lookup("AQABz0qUkZ", Duration.ofSeconds(10));

        assertThat(response.results()).isEmpty();
    }

    @Test
    void readMatchesLinkedToNoRecording() {
        acoustId.stubFor(post(urlPathEqualTo(LOOKUP_PATH))
                .atPriority(1)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {"results": [{"id": "9ff43b6a-4f16-427c-93c2-92307ca505e0", "score": 0.75}], "status": "ok"}
                        """)));

        final var result =
                client.lookup("AQABz0qUkZ", Duration.ofSeconds(10)).results().getFirst();

        assertThat(result.score()).isEqualTo(0.75);
        assertThat(result.recordings()).isNull();
    }

    @Test
    void failWhenAcoustIdRefusesTheLookup() {
        // As AcoustID answers a key it doesn't know.
        acoustId.stubFor(post(urlPathEqualTo(LOOKUP_PATH))
                .atPriority(1)
                .willReturn(badRequest()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {"error": {"code": 4, "message": "invalid API key"}, "status": "error"}
                        """)));

        assertThatThrownBy(() -> client.lookup("AQABz0qUkZ", Duration.ofSeconds(10)))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    // Built without Spring, so the @RateLimiter aspect doesn't apply.
    private static AcoustIdClient newClient(final String url) {
        final var properties = new AcoustIdProperties(url, "test-key");
        final var mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        return new AcoustIdClient(new AcoustIdConfig().acoustIdRestClient(properties, mapper), properties);
    }
}
