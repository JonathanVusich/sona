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
import org.sona.controller.request.CurrentEntryRequest;
import org.sona.controller.request.QueueModesRequest;
import org.sona.controller.request.QueuePositionRequest;
import org.sona.controller.request.ReplaceQueueRequest;
import org.sona.controller.response.QueueEntryResponse;
import org.sona.controller.response.QueueResponse;
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
import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;
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
class QueueControllerTest {

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
    List<Track> tracks;

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
        tracks = insertTracks("Don't Panic", "Shiver", "Spies", "Sparks", "Yellow", "Trouble");
    }

    @Test
    void aUserWhoNeverChangedTheirQueueHasAnEmptyOneAtVersionZero() {
        final var queue = loadQueue(alice);

        assertThat(queue).isEqualTo(new QueueResponse(0, null, 0, ShuffleMode.OFF, RepeatMode.OFF, List.of()));
    }

    @Test
    void replacingTheQueueFillsItInOrderWithTheStartCurrent() {
        final var queue = replaceQueue(alice, List.of(0, 1, 2, 1), 2);

        assertThat(queue.entries())
                .extracting(entry -> entry.track().trackId())
                .containsExactlyElementsOf(trackIds(List.of(0, 1, 2, 1)));
        assertThat(queue.entries()).extracting(QueueEntryResponse::entryId).doesNotHaveDuplicates();
        assertThat(queue.entries().get(0).track().artist().name()).isEqualTo("Coldplay");
        assertThat(queue.entries().get(0).track().release().name()).isEqualTo("Parachutes");
        assertThat(queue.currentEntryId()).isEqualTo(queue.entries().get(2).entryId());
        assertThat(loadQueue(alice)).isEqualTo(queue);
    }

    @Test
    void replacingTheQueueDropsTheOldEntriesAndStartsOverAtTheStartOfTheNewCurrentTrack() {
        replaceQueue(alice, List.of(0, 1), 1);
        updatePosition(alice, loadQueue(alice).currentEntryId(), 42);

        final var queue = replaceQueue(alice, List.of(3, 4), 0);

        assertThat(queue.entries()).hasSize(2);
        assertThat(queue.positionSeconds()).isZero();
        assertThat(queue.currentEntryId()).isEqualTo(queue.entries().getFirst().entryId());
    }

    @Test
    void aTrackThatIsNotInTheLibraryIsRefusedAndLeavesTheQueueAsItWas() {
        final var before = replaceQueue(alice, List.of(0, 1), 0);
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
    }

    @Test
    void aStartPastTheEndOfTheTracksOrAnEmptyListIsRefused() {
        final var trackIds = List.of(tracks.getFirst().trackId());

        expectInvalid(api.putJson(alice, ApiRoutes.QUEUE, new ReplaceQueueRequest(trackIds, 1)));
        expectInvalid(api.putJson(alice, ApiRoutes.QUEUE, new ReplaceQueueRequest(trackIds, -1)));
        expectInvalid(api.putJson(alice, ApiRoutes.QUEUE, new ReplaceQueueRequest(List.of(), 0)));
    }

    @Test
    void movingToAnEntryMakesItCurrentAtItsStart() {
        final var queue = replaceQueue(alice, List.of(0, 1, 2), 0);
        updatePosition(alice, queue.currentEntryId(), 30);
        final var target = queue.entries().get(2).entryId();

        api.putJson(alice, ApiRoutes.QUEUE_CURRENT, new CurrentEntryRequest(target))
                .expectStatus()
                .isNoContent();

        final var moved = loadQueue(alice);
        assertThat(moved.currentEntryId()).isEqualTo(target);
        assertThat(moved.positionSeconds()).isZero();
    }

    @Test
    void anotherUsersEntryOrOneThatDoesNotExistCannotBeMadeCurrent() {
        final var aliceQueue = replaceQueue(alice, List.of(0, 1), 0);
        replaceQueue(bob, List.of(2), 0);

        for (final var entryId : List.of(aliceQueue.entries().get(1).entryId(), uuidv7())) {
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
        final var other = queue.entries().get(1).entryId();

        updatePosition(alice, queue.currentEntryId(), 42);
        assertThat(loadQueue(alice).positionSeconds()).isEqualTo(42);

        updatePosition(alice, other, 7);
        assertThat(loadQueue(alice).positionSeconds()).isEqualTo(42);
    }

    @Test
    void everyChangeRaisesTheVersion() {
        final var replaced = replaceQueue(alice, List.of(0, 1), 0);
        updatePosition(alice, replaced.currentEntryId(), 5);
        final var reported = loadQueue(alice).version();
        updateModes(alice, ShuffleMode.OFF, RepeatMode.ALL);
        final var modes = loadQueue(alice).version();

        assertThat(replaced.version()).isPositive();
        assertThat(reported).isGreaterThan(replaced.version());
        assertThat(modes).isGreaterThan(reported);
    }

    @Test
    void turningShuffleOnReordersOnlyTheEntriesAfterTheCurrentOneAndTurningItOffRestoresTheirOrder() {
        final var queue = replaceQueue(alice, List.of(0, 1, 2, 3, 4, 5), 2);
        final var entryIds = entryIds(queue);

        updateModes(alice, ShuffleMode.ON, RepeatMode.OFF);
        final var shuffled = loadQueue(alice);

        assertThat(shuffled.shuffle()).isEqualTo(ShuffleMode.ON);
        assertThat(entryIds(shuffled).subList(0, 3)).isEqualTo(entryIds.subList(0, 3));
        assertThat(entryIds(shuffled).subList(3, 6)).containsExactlyInAnyOrderElementsOf(entryIds.subList(3, 6));
        assertThat(shuffled.currentEntryId()).isEqualTo(queue.currentEntryId());

        updateModes(alice, ShuffleMode.OFF, RepeatMode.OFF);
        assertThat(entryIds(loadQueue(alice))).isEqualTo(entryIds);
    }

    @Test
    void replacingAShuffledQueuePlaysTheStartFirstAndTheRestInSomeOrder() {
        updateModes(alice, ShuffleMode.ON, RepeatMode.ONE);

        final var queue = replaceQueue(alice, List.of(0, 1, 2, 3, 4, 5), 3);

        assertThat(queue.shuffle()).isEqualTo(ShuffleMode.ON);
        assertThat(queue.repeat()).isEqualTo(RepeatMode.ONE);
        assertThat(queue.entries().getFirst().track().trackId())
                .isEqualTo(tracks.get(3).trackId());
        assertThat(queue.currentEntryId()).isEqualTo(queue.entries().getFirst().entryId());
        assertThat(queue.entries())
                .extracting(entry -> entry.track().trackId())
                .containsExactlyInAnyOrderElementsOf(trackIds(List.of(0, 1, 2, 3, 4, 5)));
    }

    @Test
    void clearingTheQueueEmptiesItButKeepsTheModes() {
        replaceQueue(alice, List.of(0, 1), 1);
        updateModes(alice, ShuffleMode.ON, RepeatMode.ALL);

        api.deleteWithToken(ApiRoutes.QUEUE, alice).expectStatus().isNoContent();

        final var queue = loadQueue(alice);
        assertThat(queue.entries()).isEmpty();
        assertThat(queue.currentEntryId()).isNull();
        assertThat(queue.positionSeconds()).isZero();
        assertThat(queue.shuffle()).isEqualTo(ShuffleMode.ON);
        assertThat(queue.repeat()).isEqualTo(RepeatMode.ALL);
    }

    @Test
    void usersHaveQueuesOfTheirOwn() {
        final var aliceQueue = replaceQueue(alice, List.of(0, 1), 0);

        assertThat(loadQueue(bob).entries()).isEmpty();
        replaceQueue(bob, List.of(2), 0);
        api.deleteWithToken(ApiRoutes.QUEUE, bob).expectStatus().isNoContent();

        assertThat(loadQueue(alice)).isEqualTo(aliceQueue);
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

    private static List<UUID> entryIds(final QueueResponse queue) {
        return queue.entries().stream().map(QueueEntryResponse::entryId).toList();
    }

    private List<UUID> trackIds(final List<Integer> trackNumbers) {
        return trackNumbers.stream().map(number -> tracks.get(number).trackId()).toList();
    }

    private List<Track> insertTracks(final String... names) {
        return IntStream.range(0, names.length)
                .mapToObj(index -> insertTrack(names[index], index + 1, IngestState.COMPLETED))
                .toList();
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
