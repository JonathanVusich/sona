package org.sona.samples;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import java.util.List;
import java.util.UUID;
import lombok.experimental.UtilityClass;

/**
 * MusicBrainz responses for an album of two four-minute songs by Coldplay, "Song One" and "Song Two", which the
 * recorded responses have no album as small as.
 */
@UtilityClass
public final class TwoTrackAlbum {

    public static final UUID RELEASE_ID = UUID.fromString("a1b1c1d1-0000-4000-8000-000000000101");
    private static final UUID RELEASE_GROUP_ID = UUID.fromString("a1b1c1d1-0000-4000-8000-000000000102");
    private static final UUID SONG_ONE_ID = UUID.fromString("a1b1c1d1-0000-4000-8000-000000000111");
    private static final UUID SONG_TWO_ID = UUID.fromString("a1b1c1d1-0000-4000-8000-000000000112");
    // Looked up from the recorded response.
    private static final String COLDPLAY_CREDIT = """
            {"name": "Coldplay", "artist": {"id": "cc197bad-dc9c-440d-a5b5-d52ba2e14234", "name": "Coldplay"}}""";

    /**
     * Answers the songs' searches and the release's lookup, above the recorded stubs. The server is shared with other
     * tests, so the caller removes the stubs with {@link #removeStubs} when it is done.
     *
     * @return the IDs of the stubs
     */
    public static List<UUID> stub(final WireMockServer musicBrainz) {
        return List.of(
                stub(musicBrainz, searchStub("Song One"), searchJson(SONG_ONE_ID, "Song One")),
                stub(musicBrainz, searchStub("Song Two"), searchJson(SONG_TWO_ID, "Song Two")),
                stub(musicBrainz, get(urlPathEqualTo("/ws/2/release/" + RELEASE_ID)), releaseJson()));
    }

    public static void removeStubs(final WireMockServer musicBrainz, final List<UUID> stubIds) {
        for (final var stubId : stubIds) {
            musicBrainz.removeStubMapping(musicBrainz.getStubMapping(stubId).getItem());
        }
    }

    private static UUID stub(final WireMockServer musicBrainz, final MappingBuilder request, final String body) {
        final var stubId = UUID.randomUUID();
        musicBrainz.stubFor(request.withId(stubId)
                .atPriority(1)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
        return stubId;
    }

    private static MappingBuilder searchStub(final String title) {
        return get(urlPathEqualTo("/ws/2/recording")).withQueryParam("query", containing("\"" + title + "\""));
    }

    private static String searchJson(final UUID recordingId, final String title) {
        return """
                {"recordings": [{"id": "%s", "score": 100, "title": "%s", "length": 240000, "artist-credit": [%s],
                    "releases": [{"id": "%s", "title": "Two Songs", "track-count": 2,
                        "media": [{"position": 1, "track-count": 2, "track-offset": 0}]}]}]}""".formatted(recordingId, title, COLDPLAY_CREDIT, RELEASE_ID);
    }

    private static String releaseJson() {
        return """
                {"id": "%s", "title": "Two Songs", "date": "2002-08-26", "country": "GB", "artist-credit": [%s],
                 "label-info": [],
                 "release-group": {"id": "%s", "title": "Two Songs", "primary-type": "Album",
                    "first-release-date": "2002-08-26", "artist-credit": [%s]},
                 "media": [{"position": 1, "format": "CD", "track-count": 2, "tracks": [%s, %s]}]}""".formatted(
                        RELEASE_ID,
                        COLDPLAY_CREDIT,
                        RELEASE_GROUP_ID,
                        COLDPLAY_CREDIT,
                        trackJson(1, SONG_ONE_ID, "Song One"),
                        trackJson(2, SONG_TWO_ID, "Song Two"));
    }

    private static String trackJson(final int position, final UUID recordingId, final String title) {
        return """
                {"id": "%s", "position": %d, "number": "%d", "title": "%s", "length": 240000,
                 "recording": {"id": "%s", "title": "%s", "length": 240000}, "artist-credit": [%s]}""".formatted(UUID.randomUUID(), position, position, title, recordingId, title, COLDPLAY_CREDIT);
    }
}
