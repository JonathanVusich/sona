package org.sona.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.TestUsers;
import org.sona.controller.response.ArtistDetailResponse;
import org.sona.controller.response.ArtistResponse;
import org.sona.controller.response.ReleaseDetailResponse;
import org.sona.controller.response.ReleaseGroupDetailResponse;
import org.sona.controller.response.ReleaseGroupResponse;
import org.sona.controller.response.ReleaseResponse;
import org.sona.controller.response.TrackResponse;
import org.sona.db.ArtistDao;
import org.sona.db.PasswordDao;
import org.sona.db.LabelDao;
import org.sona.db.MediumDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseLabelDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TestMusic;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.db.UserDao;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class LibraryControllerTest {

    @Autowired
    UserDao userDao;
    @Autowired
    PasswordDao passwordDao;
    @Autowired
    PasswordEncoder encoder;
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
    TestMusic music;
    String accessToken;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        music = new TestMusic(artistDao, releaseGroupDao, releaseDao, labelDao, releaseLabelDao, mediumDao, trackDao,
                trackToIngestDao);
        new TestUsers(userDao, passwordDao, encoder).createUser("alice", UserRole.USER);
        accessToken = api.startSession("alice", "alice").accessToken();
    }

    @Test
    void artistsWithReleaseGroupsAreListedByName() {
        final var mira = music.createArtist("Mira Vale", "SE");
        final var clara = music.createArtist("Clara Holm", "NO");
        music.createArtist("Featured Only", null);
        music.createReleaseGroup("Night Garden", mira, "2019");
        music.createReleaseGroup("Winter Rooms", clara, "2017");

        assertThat(get(ApiRoutes.ARTISTS, new ParameterizedTypeReference<List<ArtistResponse>>() { }))
                .containsExactly(artistResponse(clara), artistResponse(mira));
    }

    @Test
    void artistsComeAPageAfterTheLastOneSeen() {
        final var artists = List.of(music.createArtist("A", null), music.createArtist("B", null),
                music.createArtist("C", null));
        for (final var artist : artists) {
            music.createReleaseGroup(artist.name(), artist, null);
        }

        final var page = get(ApiRoutes.ARTISTS + "?after=" + artists.get(1).artistId(),
                new ParameterizedTypeReference<List<ArtistResponse>>() { });

        assertThat(page).containsExactly(artistResponse(artists.get(2)));
    }

    @Test
    void releaseGroupsAreListedByNameOrNewestFirst() {
        final var artist = music.createArtist("Echo Parade", "US");
        final var signal = music.createReleaseGroup("Signal", artist, "2023");
        final var interference = music.createReleaseGroup("Interference", artist, "2021");
        final var type = new ParameterizedTypeReference<List<ReleaseGroupResponse>>() { };

        assertThat(get(ApiRoutes.RELEASE_GROUPS, type)).containsExactly(
                releaseGroupResponse(interference, artist), releaseGroupResponse(signal, artist));
        assertThat(get(ApiRoutes.RELEASE_GROUPS + "?order=NEWEST", type)).containsExactly(
                releaseGroupResponse(interference, artist), releaseGroupResponse(signal, artist));
        assertThat(get(ApiRoutes.RELEASE_GROUPS + "?order=NEWEST&after=" + interference.releaseGroupId(), type))
                .containsExactly(releaseGroupResponse(signal, artist));
    }

    @Test
    void anArtistShowsTheirReleaseGroupsNewestFirstAndTheMainReleasesTracks() {
        final var artist = music.createArtist("The Low Tides", "GB");
        final var guest = music.createArtist("Mira Vale", "SE");
        final var tidepool = music.createReleaseGroup("Tidepool", artist, "2019");
        final var tidepoolRelease = music.createRelease(tidepool, "Tidepool", artist, "2019");
        final var anemone = music.createTrack(tidepoolRelease, "Anemone", 150, artist);
        final var saltwater = music.createReleaseGroup("Saltwater", artist, "2021");
        final var deluxe = music.createRelease(saltwater, "Saltwater (Deluxe Edition)", artist, "2022");
        final var original = music.createRelease(saltwater, "Saltwater", artist, "2021-03");
        music.createTrack(deluxe, "Harbor Lights (Acoustic)", 180, artist);
        final var harborLights = music.createTrack(original, "Harbor Lights", 200, artist);
        final var guestSpot = music.createTrack(original, "Guest Spot", 120, guest);

        final var response = get(ApiRoutes.ARTISTS + "/" + artist.artistId(), ArtistDetailResponse.class);

        assertThat(response.country()).isEqualTo("GB");
        assertThat(response.releaseGroups()).containsExactly(
                releaseGroupResponse(saltwater, artist), releaseGroupResponse(tidepool, artist));
        assertThat(response.tracks()).containsExactlyInAnyOrder(trackResponse(harborLights, original, artist),
                trackResponse(guestSpot, original, guest), trackResponse(anemone, tidepoolRelease, artist));
    }

    @Test
    void aReleaseGroupShowsItsReleasesEarliestFirstAndTheMainReleasesTracks() {
        final var artist = music.createArtist("Mira Vale", "SE");
        final var nightGarden = music.createReleaseGroup("Night Garden", artist, "2019-05");
        final var vinyl = music.createRelease(nightGarden, "Night Garden (Vinyl)", artist, "2020");
        final var undated = music.createRelease(nightGarden, "Night Garden (Promo)", artist, null);
        final var digital = music.createRelease(nightGarden, "Night Garden", artist, "2019-05-03");
        final var paperMoons = music.createTrack(digital, "Paper Moons", 210, artist);
        final var smallHours = music.createTrack(digital, "Small Hours", 190, artist);
        music.createTrack(vinyl, "Paper Moons", 210, artist);

        final var response = get(ApiRoutes.RELEASE_GROUPS + "/" + nightGarden.releaseGroupId(),
                ReleaseGroupDetailResponse.class);

        assertThat(response).isEqualTo(new ReleaseGroupDetailResponse(nightGarden.releaseGroupId(), "Night Garden",
                ReleaseGroupType.ALBUM, LocalDate.of(2019, 5, 1), artistResponse(artist),
                List.of(releaseResponse(digital, 2), releaseResponse(vinyl, 1), releaseResponse(undated, 0)),
                List.of(trackResponse(paperMoons, digital, artist), trackResponse(smallHours, digital, artist))));
    }

    @Test
    void aReleaseShowsItsDetailsAndTracksWithTheirOwnArtists() {
        final var artist = music.createArtist("Echo Parade", "US");
        final var guest = music.createArtist("Mira Vale", "SE");
        final var signal = music.createReleaseGroup("Signal", artist, "2023");
        final var release = music.createRelease(signal, "Signal", artist, "2023-09-01");
        final var staticBloom = music.createTrack(release, "Static Bloom", 240, artist);
        final var relay = music.createTrack(release, "Relay", 200, guest);

        final var response = get(ApiRoutes.RELEASES + "/" + release.releaseId(), ReleaseDetailResponse.class);

        assertThat(response).isEqualTo(new ReleaseDetailResponse(release.releaseId(), "Signal", LocalDate.of(2023, 9, 1), "GB",
                List.of("Parlophone"), List.of("Digital Media"), signal.releaseGroupId(), artistResponse(artist),
                List.of(trackResponse(staticBloom, release, artist), trackResponse(relay, release, guest))));
    }

    @Test
    void somethingNotInTheLibraryIsNotFound() {
        final var unknownId = uuidv7();

        api.getWithToken(ApiRoutes.ARTISTS + "/" + unknownId, accessToken)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(300);
        api.getWithToken(ApiRoutes.RELEASE_GROUPS + "/" + unknownId, accessToken)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(301);
        api.getWithToken(ApiRoutes.RELEASES + "/" + unknownId, accessToken)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(302);
    }

    @Test
    void parametersSonaCantReadAreRefused() {
        api.getWithToken(ApiRoutes.ARTISTS + "?after=not-an-id", accessToken)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(400);
        api.getWithToken(ApiRoutes.ARTISTS + "/not-an-id", accessToken)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(400);
        api.getWithToken(ApiRoutes.RELEASE_GROUPS + "?order=SIDEWAYS", accessToken)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(400);
    }

    @Test
    void browsingNeedsASignedInUser() {
        api.getAnonymously(ApiRoutes.ARTISTS)
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(100);
    }

    private <T> T get(final String path, final Class<T> type) {
        return api.getWithToken(path, accessToken).expectStatus().isOk()
                .expectBody(type).returnResult().getResponseBody();
    }

    private <T> T get(final String path, final ParameterizedTypeReference<T> type) {
        return api.getWithToken(path, accessToken).expectStatus().isOk()
                .expectBody(type).returnResult().getResponseBody();
    }

    private static ArtistResponse artistResponse(final Artist artist) {
        return new ArtistResponse(artist.artistId(), artist.name(), artist.country());
    }

    private static ReleaseGroupResponse releaseGroupResponse(final ReleaseGroup releaseGroup, final Artist artist) {
        return new ReleaseGroupResponse(releaseGroup.releaseGroupId(), releaseGroup.name(), releaseGroup.type(),
                releaseGroup.firstReleaseDate(), artistResponse(artist));
    }

    private static ReleaseResponse releaseResponse(final Release release, final int trackCount) {
        return new ReleaseResponse(release.releaseId(), release.name(), release.date(), release.country(),
                List.of("Parlophone"), List.of("Digital Media"), trackCount);
    }

    private static TrackResponse trackResponse(final Track track, final Release release, final Artist artist) {
        return new TrackResponse(track.trackId(), track.name(), track.duration(), track.discNumber(),
                track.trackNumber(), release.releaseId(), release.name(), artistResponse(artist));
    }
}
