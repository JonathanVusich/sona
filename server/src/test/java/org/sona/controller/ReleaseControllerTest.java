package org.sona.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.response.ArtistResponse;
import org.sona.controller.response.LabelResponse;
import org.sona.controller.response.MediumResponse;
import org.sona.controller.response.ReleaseResponse;
import org.sona.controller.response.TrackResponse;
import org.sona.db.ArtistDao;
import org.sona.db.LabelDao;
import org.sona.db.MediumDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.ReleaseLabelDao;
import org.sona.db.TestLibrary;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Track;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ReleaseControllerTest {

    @Autowired
    JwtEncoder encoder;
    @Autowired
    ArtistDao artistDao;
    @Autowired
    ReleaseGroupDao releaseGroupDao;
    @Autowired
    ReleaseDao releaseDao;
    @Autowired
    LabelDao labelDao;
    @Autowired
    ReleaseLabelDao releaseLabelDao;
    @Autowired
    MediumDao mediumDao;
    @Autowired
    TrackDao trackDao;
    @Autowired
    TrackToIngestDao trackToIngestDao;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    TestLibrary library;
    String token;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        library = new TestLibrary(artistDao, releaseGroupDao, releaseDao, labelDao, releaseLabelDao, mediumDao,
                trackDao, trackToIngestDao);
        token = new TestTokens(encoder).accessToken(uuidv7(), "alice", UserRole.USER, SessionState.ACTIVE);
    }

    @Test
    void aReleaseComesWithItsArtistLabelsAndMediaEachWithItsTracks() {
        final var various = library.storeArtist("Various Artists");
        final var coldplay = library.storeArtist("Coldplay");
        final var keane = library.storeArtist("Keane");
        final var release = library.storeRelease(various, "Britpop");
        final var parlophone = library.storeLabel(release, "Parlophone");
        final var disc1 = library.storeMedium(release, 1);
        final var disc2 = library.storeMedium(release, 2);
        final var yellow = library.storeTrack(disc1, coldplay, 1, "Yellow");
        final var somewhereOnlyWeKnow = library.storeTrack(disc2, keane, 1, "Somewhere Only We Know");

        final var response = api.getWithToken(ApiRoutes.RELEASES + "/" + release.releaseId(), token)
                .expectStatus().isOk()
                .expectBody(ReleaseResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isEqualTo(new ReleaseResponse(release.releaseId(), "Britpop", LocalDate.of(2002, 8, 26),
                "GB", 2, release.releaseGroupId(), artist(various),
                List.of(new LabelResponse(parlophone.labelId(), "Parlophone")),
                List.of(new MediumResponse(1, "CD", List.of(track(yellow, coldplay))),
                        new MediumResponse(2, "CD", List.of(track(somewhereOnlyWeKnow, keane))))));
    }

    @Test
    void anUnknownReleaseIsNotFound() {
        api.getWithToken(ApiRoutes.RELEASES + "/" + uuidv7(), token)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(300)
                .jsonPath("$.reason").isEqualTo("This release isn't in the library.");
    }

    @Test
    void anIdThatIsntAUuidIsRefused() {
        api.getWithToken(ApiRoutes.RELEASES + "/yellow", token)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400)
                .jsonPath("$.reason").isEqualTo("The request has a parameter Sona can't read.");
    }

    private static ArtistResponse artist(final Artist artist) {
        return new ArtistResponse(artist.artistId(), artist.name());
    }

    private static TrackResponse track(final Track track, final Artist artist) {
        return new TrackResponse(track.trackId(), track.name(), track.duration(), track.position(), artist(artist));
    }
}
