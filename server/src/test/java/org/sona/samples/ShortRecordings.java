package org.sona.samples;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import lombok.experimental.UtilityClass;

/**
 * MusicBrainz responses for recordings as short as the {@link Mp3Sample}s, which are seconds long. The recorded ones are
 * minutes long, and a recording must be within seconds of the audio to be matched.
 */
@UtilityClass
public final class ShortRecordings {

    private static final String LOWER_YOUR_EYELIDS_ID = "cd2e7c47-16f5-46c6-a37c-a1eb7bf599ff";
    private static final String LOWER_YOUR_EYELIDS_FILE =
            "/wiremock/__files/recording/lower-your-eyelids-to-die-with-the-sun.json";

    /**
     * Answers every recording search with a one-second A Whisper, above the recorded stubs. The server is shared with
     * other tests, so the caller removes the stub with {@link #removeStub} when it is done.
     *
     * @return the ID of the stub
     */
    public static UUID stubWhisperSearch(final WireMockServer musicBrainz) {
        final var stubId = UUID.randomUUID();
        musicBrainz.stubFor(get(urlPathEqualTo("/ws/2/recording"))
                .withId(stubId)
                .atPriority(1)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {"recordings": [{"id": "53616964-f51e-4ce0-a589-a5581f771197", "score": 100,
                            "title": "A Whisper", "length": 1000,
                            "artist-credit": [{"name": "Coldplay",
                                "artist": {"id": "cc197bad-dc9c-440d-a5b5-d52ba2e14234", "name": "Coldplay"}}],
                            "releases": [{"id": "5b59d66b-8902-4350-b2e3-324037ef4cb0",
                                "title": "A Rush of Blood to the Head",
                                "media": [{"position": 1, "track-count": 11, "track-offset": 8}]}]}]}
                        """)));
        return stubId;
    }

    /**
     * Answers the lookup of Lower Your Eyelids to Die With the Sun with the recorded response, but with another length.
     *
     * @return the ID of the stub
     */
    public static UUID stubLowerYourEyelidsLookup(final WireMockServer musicBrainz, final Duration length) {
        final var stubId = UUID.randomUUID();
        musicBrainz.stubFor(get(urlPathEqualTo("/ws/2/recording/" + LOWER_YOUR_EYELIDS_ID))
                .withId(stubId)
                .atPriority(1)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(recordedLookup().replace("\"length\": 637333", "\"length\": " + length.toMillis()))));
        return stubId;
    }

    public static void removeStub(final WireMockServer musicBrainz, final UUID stubId) {
        musicBrainz.removeStubMapping(musicBrainz.getStubMapping(stubId).getItem());
    }

    private static String recordedLookup() {
        try (final var inputStream = ShortRecordings.class.getResourceAsStream(LOWER_YOUR_EYELIDS_FILE)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
