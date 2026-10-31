package org.sona.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.request.CurrentEntryRequest;
import org.sona.controller.request.QueueModesRequest;
import org.sona.controller.request.QueuePositionRequest;
import org.sona.controller.request.QueueStartRequest;
import org.sona.controller.request.ReplaceQueueRequest;
import org.sona.controller.response.QueueEntryPageResponse;
import org.sona.controller.response.QueueEntryResponse;
import org.sona.controller.response.QueueResponse;
import org.sona.db.ArtistDao;
import org.sona.db.IngestDao;
import org.sona.db.PlaylistDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Playlist;
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
class QueueControllerTest {

    private static final int PAGE_SIZE = 2;

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

    @Autowired
    PlaylistDao playlistDao;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    Users aliceUser;
    String alice;
    String bob;
    Artist coldplay;
    Release parachutes;
    Medium parachutesDisc;
    List<Track> tracks;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        aliceUser = insertUser("alice");
        alice = accessToken(aliceUser);
        bob = accessToken(insertUser("bob"));
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
        tracks = insertTracks("Don't Panic", "Shiver", "Spies", "Sparks", "Yellow", "Trouble");
    }

    @Test
    void aUserWhoNeverChangedTheirQueueHasAnEmptyOneAtVersionZero() {
        final var queue = loadQueue(alice);

        assertThat(queue).isEqualTo(new QueueResponse(0, null, 0, ShuffleMode.OFF, RepeatMode.OFF));
        assertThat(loadEntries(alice)).isEmpty();
    }

    @Test
    void replacingTheQueueFillsItInOrderWithTheStartCurrent() {
        final var queue = replaceQueue(alice, List.of(0, 1, 2, 1), 2);

        final var entries = loadEntries(alice);
        assertThat(entries)
                .extracting(entry -> entry.track().trackId())
                .containsExactlyElementsOf(trackIds(List.of(0, 1, 2, 1)));
        assertThat(entries).extracting(QueueEntryResponse::entryId).doesNotHaveDuplicates();
        assertThat(entries).extracting(QueueEntryResponse::playOrder).containsExactly(1, 2, 3, 4);
        assertThat(entries.get(0).track().artist().name()).isEqualTo("Coldplay");
        assertThat(entries.get(0).track().release().name()).isEqualTo("Parachutes");
        assertThat(queue.currentEntry()).isEqualTo(entries.get(2));
        assertThat(loadQueue(alice)).isEqualTo(queue);
    }

    @Test
    void replacingTheQueueDropsTheOldEntriesAndStartsOverAtTheStartOfTheNewCurrentTrack() {
        replaceQueue(alice, List.of(0, 1), 1);
        updatePosition(alice, loadQueue(alice).currentEntry().entryId(), 42);

        final var queue = replaceQueue(alice, List.of(3, 4), 0);

        final var entries = loadEntries(alice);
        assertThat(entries).hasSize(2);
        assertThat(queue.positionSeconds()).isZero();
        assertThat(queue.currentEntry()).isEqualTo(entries.getFirst());
    }

    @Test
    void aTrackThatIsNotInTheLibraryIsRefusedAndLeavesTheQueueAsItWas() {
        final var before = replaceQueue(alice, List.of(0, 1), 0);
        final var entriesBefore = loadEntries(alice);
        final var notImported = insertTrack("Everything's Not Lost", 7, IngestState.MOVE_FAILED);

        for (final var trackId : List.of(uuidv7(), notImported.trackId())) {
            api.putJson(
                            alice,
                            ApiRoutes.QUEUE,
                            new ReplaceQueueRequest(List.of(tracks.get(2).trackId(), trackId), 0))
                    .expectStatus()
                    .isNotFound()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(306);
        }

        assertThat(loadQueue(alice)).isEqualTo(before);
        assertThat(loadEntries(alice)).isEqualTo(entriesBefore);
    }

    @Test
    void aStartPastTheEndOfTheTracksOrAnEmptyListIsRefused() {
        final var trackIds = List.of(tracks.getFirst().trackId());

        expectInvalid(api.putJson(alice, ApiRoutes.QUEUE, new ReplaceQueueRequest(trackIds, 1)));
        expectInvalid(api.putJson(alice, ApiRoutes.QUEUE, new ReplaceQueueRequest(trackIds, -1)));
        expectInvalid(api.putJson(alice, ApiRoutes.QUEUE, new ReplaceQueueRequest(List.of(), 0)));
    }

    @Test
    void entriesComeAPageAtATimeInPlayOrder() {
        replaceQueue(alice, List.of(0, 1, 2, 3, 4), 0);

        final var first = loadEntryPage(alice, ApiRoutes.QUEUE_ENTRIES + "?limit=2");
        final var second = loadEntryPage(alice, ApiRoutes.QUEUE_ENTRIES + "?limit=2&after=" + first.next());
        final var last = loadEntryPage(alice, ApiRoutes.QUEUE_ENTRIES + "?limit=2&after=" + second.next());

        assertThat(first.entries()).extracting(entry -> entry.track().trackId()).isEqualTo(trackIds(List.of(0, 1)));
        assertThat(second.entries())
                .extracting(entry -> entry.track().trackId())
                .isEqualTo(trackIds(List.of(2, 3)));
        assertThat(last.entries()).extracting(entry -> entry.track().trackId()).isEqualTo(trackIds(List.of(4)));
        assertThat(first.next()).isEqualTo(2);
        assertThat(second.next()).isEqualTo(4);
        assertThat(last.next()).isNull();
    }

    @Test
    void earlierEntriesComeAPageAtATimeGoingBack() {
        final var queue = replaceQueue(alice, List.of(0, 1, 2, 3, 4), 4);
        final var current = queue.currentEntry();

        final var nearest =
                loadEntryPage(alice, ApiRoutes.QUEUE_EARLIER_ENTRIES + "?limit=2&before=" + current.playOrder());
        final var furthest =
                loadEntryPage(alice, ApiRoutes.QUEUE_EARLIER_ENTRIES + "?limit=2&before=" + nearest.next());

        assertThat(nearest.entries())
                .extracting(entry -> entry.track().trackId())
                .isEqualTo(trackIds(List.of(2, 3)));
        assertThat(nearest.next()).isEqualTo(3);
        assertThat(furthest.entries())
                .extracting(entry -> entry.track().trackId())
                .isEqualTo(trackIds(List.of(0, 1)));
        assertThat(furthest.next()).isNull();
    }

    @Test
    void aPageCanStartAfterTheCurrentEntry() {
        final var queue = replaceQueue(alice, List.of(0, 1, 2, 3), 2);
        final var after = queue.currentEntry().playOrder();

        final var page = loadEntryPage(alice, ApiRoutes.QUEUE_ENTRIES + "?after=" + after);

        assertThat(page.entries()).extracting(entry -> entry.track().trackId()).isEqualTo(trackIds(List.of(3)));
    }

    @Test
    void pagesOfNoEntriesOrMoreThanTheLimitAreRefused() {
        expectInvalid(api.getWithToken(ApiRoutes.QUEUE_ENTRIES + "?limit=0", alice));
        expectInvalid(api.getWithToken(ApiRoutes.QUEUE_ENTRIES + "?limit=101", alice));
        expectInvalid(api.getWithToken(ApiRoutes.QUEUE_ENTRIES + "?after=-1", alice));
        expectInvalid(api.getWithToken(ApiRoutes.QUEUE_EARLIER_ENTRIES, alice));
        expectInvalid(api.getWithToken(ApiRoutes.QUEUE_EARLIER_ENTRIES + "?before=0", alice));
    }

    @Test
    void aPlaylistFillsTheQueueInItsOrderWithTheStartCurrent() {
        final var playlist = insertPlaylist(aliceUser, List.of(4, 0, 3, 1));

        final var queue = putStart(alice, ApiRoutes.QUEUE_PLAYLIST, playlist.playlistId(), 2);

        final var entries = loadEntries(alice);
        assertThat(entries)
                .extracting(entry -> entry.track().trackId())
                .containsExactlyElementsOf(trackIds(List.of(4, 0, 3, 1)));
        assertThat(queue.currentEntry()).isEqualTo(entries.get(2));
        assertThat(queue.positionSeconds()).isZero();
    }

    @Test
    void aShuffledQueueFilledFromAPlaylistPlaysTheStartFirst() {
        updateModes(alice, ShuffleMode.ON, RepeatMode.OFF);
        final var playlist = insertPlaylist(aliceUser, List.of(0, 1, 2, 3, 4, 5));

        final var queue = putStart(alice, ApiRoutes.QUEUE_PLAYLIST, playlist.playlistId(), 3);

        final var entries = loadEntries(alice);
        assertThat(entries.getFirst().track().trackId()).isEqualTo(tracks.get(3).trackId());
        assertThat(queue.currentEntry()).isEqualTo(entries.getFirst());
        assertThat(entries)
                .extracting(entry -> entry.track().trackId())
                .containsExactlyInAnyOrderElementsOf(trackIds(List.of(0, 1, 2, 3, 4, 5)));
        assertThat(entries).extracting(QueueEntryResponse::playOrder).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void anotherUsersPlaylistOrOneThatDoesNotExistIsRefusedAndLeavesTheQueueAsItWas() {
        final var before = replaceQueue(bob, List.of(0), 0);
        final var alices = insertPlaylist(aliceUser, List.of(0, 1));

        for (final var playlistId : List.of(alices.playlistId(), uuidv7())) {
            api.putJson(
                            bob,
                            ApiRoutes.QUEUE_PLAYLIST.replace("{playlistId}", playlistId.toString()),
                            new QueueStartRequest(0))
                    .expectStatus()
                    .isNotFound()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(307);
        }

        assertThat(loadQueue(bob)).isEqualTo(before);
    }

    @Test
    void aStartPastTheEndOfAPlaylistOrOnAnEmptyOneIsRefusedAndLeavesTheQueueAsItWas() {
        final var before = replaceQueue(alice, List.of(0, 1), 1);
        final var playlist = insertPlaylist(aliceUser, List.of(0, 1));
        final var empty = insertPlaylist(aliceUser, List.of());
        final var route = ApiRoutes.QUEUE_PLAYLIST;

        expectInvalid(api.putJson(
                alice, route.replace("{playlistId}", playlist.playlistId().toString()), new QueueStartRequest(2)));
        expectInvalid(api.putJson(
                alice, route.replace("{playlistId}", empty.playlistId().toString()), new QueueStartRequest(0)));

        assertThat(loadQueue(alice)).isEqualTo(before);
        assertThat(loadEntries(alice)).hasSize(2);
    }

    @Test
    void aReleaseFillsTheQueueWithItsTracksInOrder() {
        final var queue = putStart(alice, ApiRoutes.QUEUE_RELEASE, parachutes.releaseId(), 1);

        final var entries = loadEntries(alice);
        assertThat(entries)
                .extracting(entry -> entry.track().trackId())
                .containsExactlyElementsOf(trackIds(List.of(0, 1, 2, 3, 4, 5)));
        assertThat(queue.currentEntry()).isEqualTo(entries.get(1));
    }

    @Test
    void aReleaseThatIsNotInTheLibraryIsRefused() {
        api.putJson(
                        alice,
                        ApiRoutes.QUEUE_RELEASE.replace("{releaseId}", uuidv7().toString()),
                        new QueueStartRequest(0))
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(300);
    }

    @Test
    void movingToAnEntryMakesItCurrentAtItsStart() {
        final var queue = replaceQueue(alice, List.of(0, 1, 2), 0);
        updatePosition(alice, queue.currentEntry().entryId(), 30);
        final var target = loadEntries(alice).get(2);

        api.putJson(alice, ApiRoutes.QUEUE_CURRENT, new CurrentEntryRequest(target.entryId()))
                .expectStatus()
                .isNoContent();

        final var moved = loadQueue(alice);
        assertThat(moved.currentEntry()).isEqualTo(target);
        assertThat(moved.positionSeconds()).isZero();
    }

    @Test
    void anotherUsersEntryOrOneThatDoesNotExistCannotBeMadeCurrent() {
        final var aliceQueue = replaceQueue(alice, List.of(0, 1), 0);
        replaceQueue(bob, List.of(2), 0);
        final var alicesSecond = loadEntries(alice).get(1);

        for (final var entryId : List.of(alicesSecond.entryId(), uuidv7())) {
            api.putJson(bob, ApiRoutes.QUEUE_CURRENT, new CurrentEntryRequest(entryId))
                    .expectStatus()
                    .isNotFound()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(308);
        }
        assertThat(loadQueue(alice)).isEqualTo(aliceQueue);
    }

    @Test
    void thePositionOfTheCurrentEntryIsKeptAndAReportForAnotherEntryIsIgnored() {
        final var queue = replaceQueue(alice, List.of(0, 1), 0);
        final var other = loadEntries(alice).get(1).entryId();

        updatePosition(alice, queue.currentEntry().entryId(), 42);
        assertThat(loadQueue(alice).positionSeconds()).isEqualTo(42);

        updatePosition(alice, other, 7);
        assertThat(loadQueue(alice).positionSeconds()).isEqualTo(42);
    }

    @Test
    void everyChangeRaisesTheVersion() {
        final var replaced = replaceQueue(alice, List.of(0, 1), 0);
        updatePosition(alice, replaced.currentEntry().entryId(), 5);
        final var reported = loadQueue(alice).version();
        updateModes(alice, ShuffleMode.OFF, RepeatMode.ALL);
        final var modes = loadQueue(alice).version();

        assertThat(replaced.version()).isPositive();
        assertThat(reported).isGreaterThan(replaced.version());
        assertThat(modes).isGreaterThan(reported);
    }

    @Test
    void turningShuffleOnPlaysTheCurrentEntryFirstAndEveryOtherOnceAndTurningItOffRestoresTheQueuedOrder() {
        final var queue = replaceQueue(alice, List.of(0, 1, 2, 3, 4, 5), 2);
        final var entryIds = entryIds(loadEntries(alice));

        updateModes(alice, ShuffleMode.ON, RepeatMode.OFF);
        final var shuffled = loadQueue(alice);
        final var shuffledEntries = loadEntries(alice);

        assertThat(shuffled.shuffle()).isEqualTo(ShuffleMode.ON);
        assertThat(shuffled.currentEntry().entryId())
                .isEqualTo(queue.currentEntry().entryId());
        assertThat(shuffledEntries.getFirst().entryId())
                .isEqualTo(queue.currentEntry().entryId());
        assertThat(entryIds(shuffledEntries)).containsExactlyInAnyOrderElementsOf(entryIds);
        assertThat(shuffledEntries).extracting(QueueEntryResponse::playOrder).isSorted();

        updateModes(alice, ShuffleMode.OFF, RepeatMode.OFF);
        assertThat(entryIds(loadEntries(alice))).isEqualTo(entryIds);
    }

    @Test
    void turningShuffleOnPartWayStartsAtTheCurrentEntryAndWrapsRoundToTheOnesBeforeIt() {
        replaceQueue(alice, List.of(0, 1, 2, 3, 4, 5), 0);
        final var entryIds = entryIds(loadEntries(alice));
        moveTo(alice, entryIds.get(4));

        updateModes(alice, ShuffleMode.ON, RepeatMode.OFF);
        final var shuffled = loadEntries(alice);

        assertThat(shuffled.getFirst().entryId()).isEqualTo(entryIds.get(4));
        assertThat(entryIds(shuffled)).containsExactlyInAnyOrderElementsOf(entryIds);
        assertThat(shuffled).extracting(QueueEntryResponse::playOrder).isSorted();
        final var earlier = loadEntryPage(
                alice,
                ApiRoutes.QUEUE_EARLIER_ENTRIES + "?before="
                        + shuffled.getFirst().playOrder());
        assertThat(earlier.entries()).isEmpty();
    }

    @Test
    void entriesBeforeAShuffledEntryAreThoseThatPlayBeforeIt() {
        replaceQueue(alice, List.of(0, 1, 2, 3, 4, 5), 3);
        moveTo(alice, entryIds(loadEntries(alice)).get(1));
        updateModes(alice, ShuffleMode.ON, RepeatMode.OFF);
        final var shuffled = loadEntries(alice);
        final var last = shuffled.getLast();

        final var earlier =
                loadEntryPage(alice, ApiRoutes.QUEUE_EARLIER_ENTRIES + "?limit=100&before=" + last.playOrder());

        assertThat(entryIds(earlier.entries())).isEqualTo(entryIds(shuffled.subList(0, shuffled.size() - 1)));
    }

    @Test
    void changingOnlyTheRepeatModeOfAShuffledQueueKeepsItsOrder() {
        replaceQueue(alice, List.of(0, 1, 2, 3, 4, 5), 0);
        updateModes(alice, ShuffleMode.ON, RepeatMode.OFF);
        final var shuffled = loadEntries(alice);
        moveTo(alice, shuffled.get(3).entryId());
        final var version = loadQueue(alice).version();

        updateModes(alice, ShuffleMode.ON, RepeatMode.ALL);

        assertThat(loadEntries(alice)).isEqualTo(shuffled);
        assertThat(loadQueue(alice).version()).isGreaterThan(version);
    }

    @Test
    void replacingAShuffledQueuePlaysTheStartFirstAndTheRestInSomeOrder() {
        updateModes(alice, ShuffleMode.ON, RepeatMode.ONE);

        final var queue = replaceQueue(alice, List.of(0, 1, 2, 3, 4, 5), 3);

        final var entries = loadEntries(alice);
        assertThat(queue.shuffle()).isEqualTo(ShuffleMode.ON);
        assertThat(queue.repeat()).isEqualTo(RepeatMode.ONE);
        assertThat(entries.getFirst().track().trackId()).isEqualTo(tracks.get(3).trackId());
        assertThat(queue.currentEntry()).isEqualTo(entries.getFirst());
        assertThat(entries)
                .extracting(entry -> entry.track().trackId())
                .containsExactlyInAnyOrderElementsOf(trackIds(List.of(0, 1, 2, 3, 4, 5)));
    }

    @Test
    void clearingTheQueueEmptiesItButKeepsTheModes() {
        replaceQueue(alice, List.of(0, 1), 1);
        updateModes(alice, ShuffleMode.ON, RepeatMode.ALL);

        api.deleteWithToken(ApiRoutes.QUEUE, alice).expectStatus().isNoContent();

        final var queue = loadQueue(alice);
        assertThat(loadEntries(alice)).isEmpty();
        assertThat(queue.currentEntry()).isNull();
        assertThat(queue.positionSeconds()).isZero();
        assertThat(queue.shuffle()).isEqualTo(ShuffleMode.ON);
        assertThat(queue.repeat()).isEqualTo(RepeatMode.ALL);
    }

    @Test
    void usersHaveQueuesOfTheirOwn() {
        final var aliceQueue = replaceQueue(alice, List.of(0, 1), 0);
        final var aliceEntries = loadEntries(alice);

        assertThat(loadEntries(bob)).isEmpty();
        replaceQueue(bob, List.of(2), 0);
        api.deleteWithToken(ApiRoutes.QUEUE, bob).expectStatus().isNoContent();

        assertThat(loadQueue(alice)).isEqualTo(aliceQueue);
        assertThat(loadEntries(alice)).isEqualTo(aliceEntries);
    }

    private static void expectInvalid(final RestTestClient.ResponseSpec response) {
        response.expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo(400);
    }

    private QueueResponse loadQueue(final String accessToken) {
        return api.getWithToken(ApiRoutes.QUEUE, accessToken)
                .expectStatus()
                .isOk()
                .expectBody(QueueResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private QueueEntryPageResponse loadEntryPage(final String accessToken, final String path) {
        return api.getWithToken(path, accessToken)
                .expectStatus()
                .isOk()
                .expectBody(QueueEntryPageResponse.class)
                .returnResult()
                .getResponseBody();
    }

    /**
     * @return every entry of the queue, read {@value #PAGE_SIZE} at a time
     */
    private List<QueueEntryResponse> loadEntries(final String accessToken) {
        final var entries = new ArrayList<QueueEntryResponse>();
        var after = 0;
        while (true) {
            final var page =
                    loadEntryPage(accessToken, ApiRoutes.QUEUE_ENTRIES + "?limit=" + PAGE_SIZE + "&after=" + after);
            entries.addAll(page.entries());
            if (page.next() == null) {
                return entries;
            }
            after = page.next();
        }
    }

    /**
     * @param trackNumbers indexes into {@link #tracks}
     */
    private QueueResponse replaceQueue(final String accessToken, final List<Integer> trackNumbers, final int start) {
        return api.putJson(accessToken, ApiRoutes.QUEUE, new ReplaceQueueRequest(trackIds(trackNumbers), start))
                .expectStatus()
                .isOk()
                .expectBody(QueueResponse.class)
                .returnResult()
                .getResponseBody();
    }

    /**
     * @param route {@link ApiRoutes#QUEUE_PLAYLIST} or {@link ApiRoutes#QUEUE_RELEASE}
     */
    private QueueResponse putStart(final String accessToken, final String route, final UUID sourceId, final int start) {
        final var path = route.replaceAll("\\{[a-zA-Z]+}", sourceId.toString());
        return api.putJson(accessToken, path, new QueueStartRequest(start))
                .expectStatus()
                .isOk()
                .expectBody(QueueResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void moveTo(final String accessToken, final UUID entryId) {
        api.putJson(accessToken, ApiRoutes.QUEUE_CURRENT, new CurrentEntryRequest(entryId))
                .expectStatus()
                .isNoContent();
    }

    private void updatePosition(final String accessToken, final UUID entryId, final int seconds) {
        api.putJson(accessToken, ApiRoutes.QUEUE_POSITION, new QueuePositionRequest(entryId, seconds))
                .expectStatus()
                .isNoContent();
    }

    private void updateModes(final String accessToken, final ShuffleMode shuffle, final RepeatMode repeat) {
        api.putJson(accessToken, ApiRoutes.QUEUE_MODES, new QueueModesRequest(shuffle, repeat))
                .expectStatus()
                .isNoContent();
    }

    private static List<UUID> entryIds(final List<QueueEntryResponse> entries) {
        return entries.stream().map(QueueEntryResponse::entryId).toList();
    }

    private List<UUID> trackIds(final List<Integer> trackNumbers) {
        return trackNumbers.stream().map(number -> tracks.get(number).trackId()).toList();
    }

    /**
     * @param trackNumbers indexes into {@link #tracks}, in the playlist's order
     */
    private Playlist insertPlaylist(final Users owner, final List<Integer> trackNumbers) {
        final var playlist = new Playlist(uuidv7(), owner.userId(), "Playlist " + uuidv7(), null, 0);
        playlistDao.insert(playlist);
        if (!trackNumbers.isEmpty()) {
            playlistDao.insertTracks(owner.userId(), playlist.playlistId(), trackIds(trackNumbers));
        }
        return playlist;
    }

    private List<Track> insertTracks(final String... names) {
        return IntStream.range(0, names.length)
                .mapToObj(index -> insertTrack(names[index], index + 1, IngestState.COMPLETED))
                .toList();
    }

    private Users insertUser(final String username) {
        final var user = new Users(uuidv7(), UserType.LOCAL, username, UserRole.USER, AccountState.ACTIVE);
        userDao.insert(user);
        return user;
    }

    private String accessToken(final Users user) {
        return new TestTokens(encoder).accessToken(user.userId(), user.username(), UserRole.USER, SessionState.ACTIVE);
    }

    private Track insertTrack(final String name, final int position, final IngestState state) {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId(), 1);
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
