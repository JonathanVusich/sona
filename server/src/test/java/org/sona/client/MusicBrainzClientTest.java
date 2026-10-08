package org.sona.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sona.client.model.ArtistCredit;
import org.sona.client.model.query.RecordingQuery;
import org.sona.config.MusicBrainzConfig;
import org.sona.config.properties.MusicBrainzProperties;
import org.springframework.web.client.HttpServerErrorException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
        assertThat(credit.joinPhrase()).isEmpty();
        assertThat(credit.artist().id()).isEqualTo(UUID.fromString("cc197bad-dc9c-440d-a5b5-d52ba2e14234"));

        final var release = recording.releases().getFirst();
        assertThat(release.title()).isEqualTo("A Rush of Blood to the Head");
        assertThat(release.releaseGroup().id()).isEqualTo(UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf"));
    }

    @Test
    void readJoinPhrasesOfMultiArtistCredits() {
        // Trimmed from the live API's response for this recording and one of its releases.
        musicBrainz.stubFor(get(urlPathEqualTo(RECORDING_PATH)).willReturn(okJson("""
                {"count": 1, "offset": 0, "recordings": [{
                    "id": "e8a0291d-48d2-4468-bc66-f9564d877fa9",
                    "title": "Under Pressure",
                    "score": 100,
                    "artist-credit": [
                        {"joinphrase": " & ", "name": "David Bowie", "artist": {
                            "id": "5441c29d-3602-4898-b1a1-b77fa23b8e50", "name": "David Bowie",
                            "sort-name": "Bowie, David", "disambiguation": "English singer‐songwriter"}},
                        {"name": "Queen", "artist": {
                            "id": "0383dadf-2a4e-4d10-a46a-e9e041da8eb3", "name": "Queen",
                            "sort-name": "Queen", "disambiguation": "UK rock group"}}
                    ],
                    "releases": [{
                        "id": "3e8987fe-f6b9-42a7-a822-70f993a1e95c",
                        "title": "Summertime 7",
                        "count": 1,
                        "track-count": 46,
                        "artist-credit": [
                            {"joinphrase": " + ", "name": "DJ Jazzy Jeff", "artist": {
                                "id": "91ad641b-3b6f-4220-8352-f3f5f909d4e0", "name": "DJ Jazzy Jeff",
                                "sort-name": "Jazzy Jeff, DJ", "disambiguation": "DJ/turntablist"}},
                            {"name": "MICK", "artist": {
                                "id": "bffe9445-fb2c-4616-bf04-92e34a7e4359", "name": "Mick Boogie",
                                "sort-name": "Boogie, Mick"}}
                        ]
                    }]
                }]}
                """)));

        final var recording = client.searchRecordings(new RecordingQuery("Under Pressure", "Queen", -1, -1))
                .recordings().getFirst();

        assertThat(recording.artistCredit())
                .extracting(ArtistCredit::name, ArtistCredit::joinPhrase)
                .containsExactly(tuple("David Bowie", " & "), tuple("Queen", ""));
        assertThat(recording.releases().getFirst().artistCredit())
                .extracting(ArtistCredit::name, ArtistCredit::joinPhrase, credit -> credit.artist().name())
                .containsExactly(tuple("DJ Jazzy Jeff", " + ", "DJ Jazzy Jeff"), tuple("MICK", "", "Mick Boogie"));
    }

    @Test
    void sendQueryWithReservedCharactersIntact() {
        final var query = new RecordingQuery("Rock & Roll + {Live} 100%", "Simon & Garfunkel", -1, -1);
        musicBrainz.stubFor(get(urlPathEqualTo(RECORDING_PATH))
                .withQueryParam("query", equalTo(query.build()))
                .willReturn(okJson("""
                        {"count": 0, "offset": 0, "recordings": []}
                        """)));

        assertThat(client.searchRecordings(query).count()).isZero();
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
