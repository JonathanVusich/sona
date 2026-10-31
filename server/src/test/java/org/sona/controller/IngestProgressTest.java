package org.sona.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.request.IngestGroupRequest;
import org.sona.controller.response.IngestGroupResponse;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.db.UserDao;
import org.sona.engine.IngestWorker;
import org.sona.metadata.RawMetadataBuilder;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@IntegrationTest
class IngestProgressTest {

    @Autowired
    JwtEncoder encoder;

    @Autowired
    UserDao userDao;

    @Autowired
    IngestWorker worker;

    @Autowired
    SimpUserRegistry userRegistry;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    SocketClient sockets;
    TestTokens tokens;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        sockets = new SocketClient(port);
        tokens = new TestTokens(encoder);
    }

    @Test
    void anUploaderSeesTheirFileIngested() throws ExecutionException, InterruptedException, TimeoutException {
        final var alice = storeAdmin("alice");
        final var progress = subscribeToProgress(alice);

        final var uploaded = uploadWhisper(alice);
        worker.processPending();
        final var ingested = progress.poll(5, TimeUnit.SECONDS);

        assertThat(ingested)
                .isEqualTo(new TrackIngestResponse(
                        uploaded.trackIngestId(),
                        uploaded.groupIngestId(),
                        "09 A Whisper.flac",
                        IngestState.COMPLETED));
    }

    @Test
    void otherUploadersDoNotSeeIt() throws ExecutionException, InterruptedException, TimeoutException {
        final var alice = storeAdmin("alice");
        final var bob = storeAdmin("bob");
        final var aliceProgress = subscribeToProgress(alice);
        final var bobProgress = subscribeToProgress(bob);

        uploadWhisper(alice);
        worker.processPending();

        assertThat(aliceProgress.poll(5, TimeUnit.SECONDS)).isNotNull();
        // Sent at the same time as alice's, so it would have arrived by now.
        assertThat(bobProgress.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void aConnectionWithoutATokenIsRefused() throws ExecutionException, InterruptedException, TimeoutException {
        final var connection = sockets.connectAnonymously();

        assertThat(connection.awaitError()).isEqualTo("Connecting needs an access token");
    }

    @Test
    void aConnectionWithAnInvalidTokenIsRefused() throws ExecutionException, InterruptedException, TimeoutException {
        final var connection = sockets.connect("not-a-jwt");

        assertThat(connection.awaitError()).contains("Malformed token").doesNotContain("not-a-jwt");
    }

    @Test
    void aUserWhoCannotUploadCannotWatchIngests() throws ExecutionException, InterruptedException, TimeoutException {
        final var connection = sockets.connect(unstoredToken(UserRole.USER, SessionState.ACTIVE));

        connection.subscribeToProgress();

        assertThat(connection.awaitError()).isEqualTo("Access Denied");
    }

    @Test
    void aUserWhoMustChangeTheirPasswordCannotWatchIngests()
            throws ExecutionException, InterruptedException, TimeoutException {
        final var connection = sockets.connect(unstoredToken(UserRole.ADMIN, SessionState.PASSWORD_CHANGE_REQUIRED));

        connection.subscribeToProgress();

        assertThat(connection.awaitError()).isEqualTo("Access Denied");
    }

    private Users storeAdmin(final String username) {
        final var user = new Users(uuidv7(), UserType.LOCAL, username, UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(user);
        return user;
    }

    private String token(final Users user) {
        return tokens.accessToken(user.userId(), user.username(), user.role(), SessionState.ACTIVE);
    }

    /**
     * @return a token for a user who isn't stored, for checks that only read the token
     */
    private String unstoredToken(final UserRole role, final SessionState state) {
        return tokens.accessToken(uuidv7(), "carol", role, state);
    }

    /**
     * Subscribes, and waits until Sona has the subscription, so no message sent after this returns is missed.
     */
    private BlockingQueue<TrackIngestResponse> subscribeToProgress(final Users user)
            throws ExecutionException, InterruptedException, TimeoutException {
        final var progress = sockets.connect(token(user)).subscribeToProgress();
        final var deadline = Instant.now().plusSeconds(5);
        while (!isSubscribed(user)) {
            assertThat(Instant.now()).as("subscribed by now").isBefore(deadline);
            Thread.sleep(10);
        }
        return progress;
    }

    private boolean isSubscribed(final Users user) {
        final var socketUser = userRegistry.getUser(user.userId().toString());
        if (socketUser == null) {
            return false;
        }
        return socketUser.getSessions().stream()
                .flatMap(session -> session.getSubscriptions().stream())
                .anyMatch(subscription -> subscription.getDestination().equals(ApiRoutes.INGEST_PROGRESS));
    }

    private TrackIngestResponse uploadWhisper(final Users uploader) {
        final var token = token(uploader);
        final var group = api.createIngestGroup(
                        token,
                        new IngestGroupRequest(IngestGroupKind.ALBUM, "Parachutes", List.of("09 A Whisper.flac")))
                .expectStatus()
                .isCreated()
                .expectBody(IngestGroupResponse.class)
                .returnResult()
                .getResponseBody();
        return api.uploadTrack(
                        token, group.groupIngestId(), group.tracks().getFirst().trackIngestId(), whisper())
                .expectStatus()
                .isOk()
                .expectBody(TrackIngestResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private static byte[] whisper() {
        final var metadata = new RawMetadataBuilder(Duration.ofSeconds(240));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str("A Whisper"));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str("Coldplay"));
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(9));
        return flacFile(metadata.build());
    }
}
