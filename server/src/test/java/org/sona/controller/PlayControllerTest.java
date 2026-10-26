package org.sona.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.request.PlayRequest;
import org.sona.controller.response.ArtistResponse;
import org.sona.controller.response.PlayPageResponse;
import org.sona.controller.response.PlayResponse;
import org.sona.controller.response.PlaylistTrackResponse;
import org.sona.controller.response.TrackReleaseResponse;
import org.sona.db.ArtistDao;
import org.sona.db.IngestDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@IntegrationTest
class PlayControllerTest {

    @Autowired
    JwtEncoder encoder;

    @Autowired
    UserDao userDao;

    @Autowired
    ArtistDao artistDao;

    @Autowired
    ReleaseGroupDao releaseGroupDao;

    @Autowired
    ReleaseDao releaseDao;

    @Autowired
    TrackDao trackDao;

    @Autowired
    IngestDao ingestDao;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    String alice;
    String bob;
    Artist coldplay;
    Release parachutes;
    Medium parachutesDisc;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        alice = accessToken("alice");
        bob = accessToken("bob");
        coldplay = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var releaseGroup = releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), "Parachutes", null, null, coldplay.artistId(), 0, uuidv7()));
        parachutes = releaseDao.upsert(new Release(
                uuidv7(),
                "Parachutes",
                null,
                LocalDate.of(2000, 7, 10),
                "GB",
                0,
                releaseGroup.releaseGroupId(),
                coldplay.artistId(),
                uuidv7()));
        parachutesDisc = releaseDao
                .upsertMedia(List.of(new Medium(uuidv7(), parachutes.releaseId(), 1, "CD")))
                .getFirst();
    }

    @Test
    void playsListTheLatestPlaysFirstAndAgainForATrackPlayedAgain() {
        final var yellow = insertTrack("Yellow", 5, IngestState.COMPLETED);
        final var shiver = insertTrack("Shiver", 2, IngestState.COMPLETED);

        recordPlay(alice, yellow);
        recordPlay(alice, shiver);
        recordPlay(alice, yellow);

        assertThat(playedTracks(alice)).containsExactly(entry(yellow), entry(shiver), entry(yellow));
    }

    @Test
    void aPageHoldsTwentyPlaysByDefaultAndPointsToTheNext() {
        final var yellow = insertTrack("Yellow", 5, IngestState.COMPLETED);
        final var shiver = insertTrack("Shiver", 2, IngestState.COMPLETED);

        recordPlay(alice, yellow);
        for (var play = 0; play < 20; play++) {
            recordPlay(alice, shiver);
        }

        final var page = loadPage(alice, ApiRoutes.PLAYS);
        assertThat(page.plays()).hasSize(20);
        assertThat(page.next()).isEqualTo(page.plays().getLast().playId());
        assertThat(loadPage(alice, ApiRoutes.PLAYS + "?after=" + page.next()).plays())
                .extracting(PlayResponse::track)
                .containsExactly(entry(yellow));
    }

    @Test
    void scrollingBackThroughThePlaysListsEachOnceUntilTheLastPage() {
        final var tracks = List.of(
                insertTrack("Yellow", 5, IngestState.COMPLETED),
                insertTrack("Shiver", 2, IngestState.COMPLETED),
                insertTrack("Sparks", 4, IngestState.COMPLETED));
        for (final var track : List.of(tracks.get(0), tracks.get(1), tracks.get(2), tracks.get(0), tracks.get(1))) {
            recordPlay(alice, track);
        }
        // Another user's plays in between don't take up room on Alice's pages.
        recordPlay(bob, tracks.get(2));

        final var first = loadPage(alice, ApiRoutes.PLAYS + "?limit=2");
        final var second = loadPage(alice, ApiRoutes.PLAYS + "?limit=2&after=" + first.next());
        final var third = loadPage(alice, ApiRoutes.PLAYS + "?limit=2&after=" + second.next());

        assertThat(tracksOf(first)).containsExactly(entry(tracks.get(1)), entry(tracks.get(0)));
        assertThat(tracksOf(second)).containsExactly(entry(tracks.get(2)), entry(tracks.get(1)));
        assertThat(tracksOf(third)).containsExactly(entry(tracks.get(0)));
        assertThat(third.next()).isNull();
    }

    @Test
    void aPageHoldsAtMostOneHundredPlays() {
        api.getWithToken(ApiRoutes.PLAYS + "?limit=101", alice)
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    @Test
    void eachUserSeesOnlyTheirOwnPlays() {
        final var yellow = insertTrack("Yellow", 5, IngestState.COMPLETED);
        final var shiver = insertTrack("Shiver", 2, IngestState.COMPLETED);

        recordPlay(alice, yellow);
        recordPlay(bob, shiver);

        assertThat(playedTracks(alice)).containsExactly(entry(yellow));
        assertThat(playedTracks(bob)).containsExactly(entry(shiver));
    }

    @Test
    void noPlaysListNothing() {
        assertThat(playedTracks(alice)).isEmpty();
    }

    @Test
    void aTrackNotInTheLibraryCantBePlayed() {
        final var notImported = insertTrack("Trouble", 6, IngestState.MOVE_FAILED);

        for (final var trackId : List.of(notImported.trackId(), uuidv7())) {
            api.postJson(alice, ApiRoutes.PLAYS, new PlayRequest(trackId))
                    .expectStatus()
                    .isNotFound()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(306);
        }
        assertThat(playedTracks(alice)).isEmpty();
    }

    @Test
    void aPlayNeedsATrack() {
        api.postJson(alice, ApiRoutes.PLAYS, new PlayRequest(null))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    private void recordPlay(final String accessToken, final Track track) {
        api.postJson(accessToken, ApiRoutes.PLAYS, new PlayRequest(track.trackId()))
                .expectStatus()
                .isNoContent();
    }

    private List<PlaylistTrackResponse> playedTracks(final String accessToken) {
        return tracksOf(loadPage(accessToken, ApiRoutes.PLAYS));
    }

    private static List<PlaylistTrackResponse> tracksOf(final PlayPageResponse page) {
        return page.plays().stream().map(PlayResponse::track).toList();
    }

    private PlayPageResponse loadPage(final String accessToken, final String path) {
        return api.getWithToken(path, accessToken)
                .expectStatus()
                .isOk()
                .expectBody(PlayPageResponse.class)
                .returnResult()
                .getResponseBody();
    }

    /**
     * @return how the play history lists a track of Parachutes
     */
    private PlaylistTrackResponse entry(final Track track) {
        return new PlaylistTrackResponse(
                track.trackId(),
                track.name(),
                track.duration(),
                track.position(),
                new ArtistResponse(coldplay.artistId(), coldplay.name()),
                new TrackReleaseResponse(parachutes.releaseId(), parachutes.name()));
    }

    private String accessToken(final String username) {
        final var user = new Users(uuidv7(), UserType.LOCAL, username, UserRole.USER, AccountState.ACTIVE);
        userDao.insert(user);
        return new TestTokens(encoder).accessToken(user.userId(), username, UserRole.USER, SessionState.ACTIVE);
    }

    private Track insertTrack(final String name, final int position, final IngestState state) {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId());
        ingestDao.insertGroup(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), name + ".flac", state);
        ingestDao.insertTrackIngest(ingest);
        final var track = new Track(
                uuidv7(),
                name,
                240,
                AudioFormat.FLAC,
                name + ".flac",
                parachutesDisc.mediumId(),
                position,
                coldplay.artistId(),
                ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }
}
