package org.sona.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.db.IngestGroupDao;
import org.sona.db.TrackToIngestDao;
import org.sona.engine.IngestWorker;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@IntegrationTest
class IngestProgressTest {

    @Autowired
    JwtEncoder encoder;

    @Autowired
    IngestGroupDao ingestGroupDao;

    @Autowired
    TrackToIngestDao trackToIngestDao;

    @Autowired
    IngestWorker worker;

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
    void uploadersSeeEachFileAsItIsStoredAndIngested()
            throws ExecutionException, InterruptedException, TimeoutException {
        final var connection = sockets.connect(token(UserRole.ADMIN, SessionState.ACTIVE));
        final var progress = connection.subscribeToProgress();
        // Answered only once the subscription above is in place.
        connection.loadUnfinishedIngests();
        final var group = storeGroup();

        api.uploadTrack(
                        token(UserRole.ADMIN, SessionState.ACTIVE),
                        group.ingestGroupId(),
                        "09 A Whisper.flac",
                        whisper())
                .expectStatus()
                .isCreated();
        final var stored = nextMessage(progress);
        worker.processPending();
        final var ingested = nextMessage(progress);

        assertThat(stored.groupIngestId()).isEqualTo(group.ingestGroupId());
        assertThat(stored.originalFileName()).isEqualTo("09 A Whisper.flac");
        assertThat(stored.state()).isEqualTo(IngestState.PENDING);
        assertThat(ingested.trackIngestId()).isEqualTo(stored.trackIngestId());
        assertThat(ingested.state()).isEqualTo(IngestState.COMPLETED);
    }

    @Test
    void subscribingAnswersWithEveryIngestNotYetImported()
            throws ExecutionException, InterruptedException, TimeoutException {
        final var group = storeGroup();
        final var pending = storeIngest(group, IngestState.PENDING);
        final var failed = storeIngest(group, IngestState.FAILED);
        storeIngest(group, IngestState.COMPLETED);
        final var connection = sockets.connect(token(UserRole.ADMIN, SessionState.ACTIVE));

        final var unfinished = connection.loadUnfinishedIngests();

        assertThat(unfinished)
                .containsExactly(
                        new TrackIngestResponse(
                                pending.trackIngestId(), group.ingestGroupId(), "track.flac", IngestState.PENDING),
                        new TrackIngestResponse(
                                failed.trackIngestId(), group.ingestGroupId(), "track.flac", IngestState.FAILED));
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
        final var connection = sockets.connect(token(UserRole.USER, SessionState.ACTIVE));

        connection.subscribeToProgress();

        assertThat(connection.awaitError()).isEqualTo("Not allowed to subscribe to /topic/ingests");
    }

    @Test
    void aUserWhoMustChangeTheirPasswordCannotWatchIngests()
            throws ExecutionException, InterruptedException, TimeoutException {
        final var connection = sockets.connect(token(UserRole.ADMIN, SessionState.PASSWORD_CHANGE_REQUIRED));

        connection.subscribeToProgress();

        assertThat(connection.awaitError()).isEqualTo("Not allowed to subscribe to /topic/ingests");
    }

    private String token(final UserRole role, final SessionState state) {
        return tokens.accessToken(uuidv7(), "alice", role, state);
    }

    private IngestGroup storeGroup() {
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "A Rush of Blood to the Head");
        ingestGroupDao.insert(group);
        return group;
    }

    private TrackToIngest storeIngest(final IngestGroup group, final IngestState state) {
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "track.flac", state);
        trackToIngestDao.insert(ingest);
        return ingest;
    }

    private static TrackIngestResponse nextMessage(final BlockingQueue<TrackIngestResponse> progress)
            throws InterruptedException {
        final var message = progress.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("a progress message").isNotNull();
        return message;
    }

    private static byte[] whisper() {
        final var metadata = new RawMetadata(Duration.ofSeconds(10));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str("A Whisper"));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str("Coldplay"));
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(9));
        return flacFile(metadata);
    }
}
