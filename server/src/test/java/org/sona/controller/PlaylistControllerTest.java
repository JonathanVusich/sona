package org.sona.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.request.AddTracksRequest;
import org.sona.controller.request.CreatePlaylistRequest;
import org.sona.controller.request.MoveTrackRequest;
import org.sona.controller.request.PlaylistDetailsRequest;
import org.sona.controller.response.AddedTracksResponse;
import org.sona.controller.response.ArtistResponse;
import org.sona.controller.response.CreatedPlaylistResponse;
import org.sona.controller.response.PlaylistResponse;
import org.sona.controller.response.PlaylistSummaryResponse;
import org.sona.controller.response.PlaylistTrackResponse;
import org.sona.controller.response.PlaylistsResponse;
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
import org.sona.model.enums.IngestGroupState;
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
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
class PlaylistControllerTest {

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
        parachutes = insertRelease("Parachutes");
        parachutesDisc = insertMedium(parachutes);
    }

    @Test
    void aNewPlaylistListsItsTracksInTheOrderGivenWithTheirArtistsAndReleases() {
        final var yellow = insertTrack(parachutesDisc, "Yellow", 5, IngestState.COMPLETED);
        final var shiver = insertTrack(parachutesDisc, "Shiver", 2, IngestState.COMPLETED);
        final var notImported = insertTrack(parachutesDisc, "Trouble", 6, IngestState.MOVE_FAILED);

        final var playlistId = createPlaylist(
                alice,
                "Late Night",
                "For after midnight.",
                List.of(yellow.trackId(), shiver.trackId(), yellow.trackId(), notImported.trackId(), uuidv7()));
        final var playlist = loadPlaylist(alice, playlistId);

        assertThat(playlist)
                .isEqualTo(new PlaylistResponse(
                        playlistId, "Late Night", "For after midnight.", List.of(entry(yellow), entry(shiver))));
    }

    @Test
    void addingTracksAppendsThoseNotAlreadyOnThePlaylist() {
        final var yellow = insertTrack(parachutesDisc, "Yellow", 5, IngestState.COMPLETED);
        final var shiver = insertTrack(parachutesDisc, "Shiver", 2, IngestState.COMPLETED);
        final var sparks = insertTrack(parachutesDisc, "Sparks", 4, IngestState.COMPLETED);
        final var playlistId = createPlaylist(alice, "Focus", null, List.of(shiver.trackId()));

        final var added = api.postJson(
                        alice,
                        playlistPath(playlistId) + "/tracks",
                        new AddTracksRequest(List.of(yellow.trackId(), shiver.trackId(), sparks.trackId())))
                .expectStatus()
                .isOk()
                .expectBody(AddedTracksResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(added).isEqualTo(new AddedTracksResponse(2));
        assertThat(loadPlaylist(alice, playlistId).tracks())
                .containsExactly(entry(shiver), entry(yellow), entry(sparks));
    }

    @Test
    void playlistsAreListedByNameWithTheirTrackCountsAndTheReleaseGroupsTheyStartWith() {
        final var yellow = insertTrack(parachutesDisc, "Yellow", 5, IngestState.COMPLETED);
        final var shiver = insertTrack(parachutesDisc, "Shiver", 2, IngestState.COMPLETED);
        final var xAndY = insertRelease("X&Y");
        final var fixYou = insertTrack(insertMedium(xAndY), "Fix You", 10, IngestState.COMPLETED);
        final var roadTrip =
                createPlaylist(alice, "Road Trip", null, List.of(fixYou.trackId(), yellow.trackId(), shiver.trackId()));
        final var empty = createPlaylist(alice, "Empty", null, List.of());
        createPlaylist(bob, "Bob's", null, List.of(yellow.trackId()));

        final var playlists = api.getWithToken(ApiRoutes.PLAYLISTS, alice)
                .expectStatus()
                .isOk()
                .expectBody(PlaylistsResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(playlists.playlists())
                .containsExactly(
                        new PlaylistSummaryResponse(empty, "Empty", 0, List.of()),
                        new PlaylistSummaryResponse(roadTrip, "Road Trip", 3, List.of("X&Y", "Parachutes")));
    }

    @Test
    void removingATrackMovesTheTracksAfterItUp() {
        final var tracks = insertTracks("Don't Panic", "Shiver", "Spies", "Sparks");
        final var playlistId = createPlaylist(alice, "Focus", null, trackIds(tracks));

        api.deleteWithToken(trackPath(playlistId, tracks.get(1)), alice)
                .expectStatus()
                .isNoContent();
        // Removing it again does nothing.
        api.deleteWithToken(trackPath(playlistId, tracks.get(1)), alice)
                .expectStatus()
                .isNoContent();
        final var added = addTracks(alice, playlistId, List.of(tracks.get(1).trackId()));

        assertThat(added).isEqualTo(1);
        assertThat(loadPlaylist(alice, playlistId).tracks())
                .containsExactly(
                        entry(tracks.get(0)), entry(tracks.get(2)), entry(tracks.get(3)), entry(tracks.get(1)));
    }

    @Test
    void movingATrackShiftsTheTracksItPasses() {
        final var tracks = insertTracks("Don't Panic", "Shiver", "Spies", "Sparks");
        final var playlistId = createPlaylist(alice, "Focus", null, trackIds(tracks));

        moveTrack(playlistId, tracks.get(3), 1);
        assertThat(loadPlaylist(alice, playlistId).tracks())
                .containsExactly(
                        entry(tracks.get(3)), entry(tracks.get(0)), entry(tracks.get(1)), entry(tracks.get(2)));

        moveTrack(playlistId, tracks.get(0), 3);
        assertThat(loadPlaylist(alice, playlistId).tracks())
                .containsExactly(
                        entry(tracks.get(3)), entry(tracks.get(1)), entry(tracks.get(0)), entry(tracks.get(2)));

        // Past the end puts it last.
        moveTrack(playlistId, tracks.get(3), 99);
        assertThat(loadPlaylist(alice, playlistId).tracks())
                .containsExactly(
                        entry(tracks.get(1)), entry(tracks.get(0)), entry(tracks.get(2)), entry(tracks.get(3)));
    }

    @Test
    void renamingAPlaylistChangesItsNameAndDescription() {
        final var playlistId = createPlaylist(alice, "Late Night", "For after midnight.", List.of());

        api.putJson(alice, playlistPath(playlistId), new PlaylistDetailsRequest("Small Hours", null))
                .expectStatus()
                .isNoContent();

        final var playlist = loadPlaylist(alice, playlistId);
        assertThat(playlist.name()).isEqualTo("Small Hours");
        assertThat(playlist.description()).isNull();
    }

    @Test
    void aDeletedPlaylistIsGoneButItsTracksStayInTheLibrary() {
        final var yellow = insertTrack(parachutesDisc, "Yellow", 5, IngestState.COMPLETED);
        final var playlistId = createPlaylist(alice, "Late Night", null, List.of(yellow.trackId()));

        api.deleteWithToken(playlistPath(playlistId), alice).expectStatus().isNoContent();

        expectPlaylistNotFound(api.getWithToken(playlistPath(playlistId), alice));
        assertThat(trackDao.loadById(yellow.trackId())).isPresent();
    }

    @Test
    void anotherUsersPlaylistIsNotFound() {
        final var yellow = insertTrack(parachutesDisc, "Yellow", 5, IngestState.COMPLETED);
        final var shiver = insertTrack(parachutesDisc, "Shiver", 2, IngestState.COMPLETED);
        final var playlistId = createPlaylist(alice, "Late Night", null, List.of(yellow.trackId()));
        final var path = playlistPath(playlistId);

        expectPlaylistNotFound(api.getWithToken(path, bob));
        expectPlaylistNotFound(api.putJson(bob, path, new PlaylistDetailsRequest("Mine now", null)));
        expectPlaylistNotFound(api.postJson(bob, path + "/tracks", new AddTracksRequest(List.of(shiver.trackId()))));
        expectPlaylistNotFound(api.putJson(bob, trackPath(playlistId, yellow) + "/position", new MoveTrackRequest(1)));
        expectPlaylistNotFound(api.deleteWithToken(trackPath(playlistId, yellow), bob));
        expectPlaylistNotFound(api.deleteWithToken(path, bob));
        final var playlist = loadPlaylist(alice, playlistId);
        assertThat(playlist.name()).isEqualTo("Late Night");
        assertThat(playlist.tracks()).containsExactly(entry(yellow));
    }

    @Test
    void aPlaylistNeedsAName() {
        api.postJson(alice, ApiRoutes.PLAYLISTS, new CreatePlaylistRequest(" ", null, List.of()))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    private static void expectPlaylistNotFound(final RestTestClient.ResponseSpec response) {
        response.expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(307)
                .jsonPath("$.reason")
                .isEqualTo("This playlist doesn't exist.");
    }

    private UUID createPlaylist(
            final String accessToken, final String name, final String description, final List<UUID> trackIds) {
        return api.postJson(accessToken, ApiRoutes.PLAYLISTS, new CreatePlaylistRequest(name, description, trackIds))
                .expectStatus()
                .isCreated()
                .expectBody(CreatedPlaylistResponse.class)
                .returnResult()
                .getResponseBody()
                .playlistId();
    }

    private int addTracks(final String accessToken, final UUID playlistId, final List<UUID> trackIds) {
        return api.postJson(accessToken, playlistPath(playlistId) + "/tracks", new AddTracksRequest(trackIds))
                .expectStatus()
                .isOk()
                .expectBody(AddedTracksResponse.class)
                .returnResult()
                .getResponseBody()
                .added();
    }

    private void moveTrack(final UUID playlistId, final Track track, final int position) {
        api.putJson(alice, trackPath(playlistId, track) + "/position", new MoveTrackRequest(position))
                .expectStatus()
                .isNoContent();
    }

    private PlaylistResponse loadPlaylist(final String accessToken, final UUID playlistId) {
        return api.getWithToken(playlistPath(playlistId), accessToken)
                .expectStatus()
                .isOk()
                .expectBody(PlaylistResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private static String playlistPath(final UUID playlistId) {
        return ApiRoutes.PLAYLISTS + "/" + playlistId;
    }

    private static String trackPath(final UUID playlistId, final Track track) {
        return playlistPath(playlistId) + "/tracks/" + track.trackId();
    }

    /**
     * @return a track of Parachutes for each name, numbered in order
     */
    private List<Track> insertTracks(final String... names) {
        return IntStream.range(0, names.length)
                .mapToObj(index -> insertTrack(parachutesDisc, names[index], index + 1, IngestState.COMPLETED))
                .toList();
    }

    private static List<UUID> trackIds(final List<Track> tracks) {
        return tracks.stream().map(Track::trackId).toList();
    }

    /**
     * @return how the playlist page lists a track of Parachutes
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

    private Release insertRelease(final String name) {
        final var releaseGroup =
                releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), name, null, null, coldplay.artistId(), 0, uuidv7()));
        return releaseDao.upsert(new Release(
                uuidv7(),
                name,
                null,
                LocalDate.of(2000, 7, 10),
                "GB",
                0,
                releaseGroup.releaseGroupId(),
                coldplay.artistId(),
                uuidv7()));
    }

    private Medium insertMedium(final Release release) {
        return releaseDao
                .upsertMedia(List.of(new Medium(uuidv7(), release.releaseId(), 1, "CD")))
                .getFirst();
    }

    private Track insertTrack(final Medium medium, final String name, final int position, final IngestState state) {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(
                uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId(), IngestGroupState.MATCHED);
        ingestDao.insertGroup(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), name + ".flac", state);
        ingestDao.insertTrackIngest(ingest);
        final var track = new Track(
                uuidv7(),
                name,
                240,
                AudioFormat.FLAC,
                name + ".flac",
                medium.mediumId(),
                position,
                coldplay.artistId(),
                ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }
}
