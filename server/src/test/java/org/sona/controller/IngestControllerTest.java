package org.sona.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.model.Tables.INGEST_GROUP;
import static org.sona.model.Tables.TRACK_TO_INGEST;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

import java.io.IOException;
import java.nio.channels.Channels;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.request.IngestGroupRequest;
import org.sona.controller.response.IngestGroupResponse;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.db.IngestDao;
import org.sona.db.UserDao;
import org.sona.library.Library;
import org.sona.metadata.RawMetadata;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestGroupState;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
class IngestControllerTest {

    @Autowired
    JwtEncoder encoder;

    @Autowired
    IngestDao ingestDao;

    @Autowired
    UserDao userDao;

    @Autowired
    Library library;
    // For rows the DAOs have no lookup for.
    @Autowired
    DSLContext dsl;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    Users admin;
    String adminToken;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        admin = new Users(uuidv7(), UserType.LOCAL, "alice", UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(admin);
        adminToken = new TestTokens(encoder).accessToken(admin.userId(), "alice", UserRole.ADMIN, SessionState.ACTIVE);
    }

    @Test
    void aGroupIsCreatedWithAnIngestForEachOfItsFiles() {
        final var created = createGroup(request("Parachutes", "01 Don't Panic.flac", "02 Shiver.flac"));

        assertThat(created.kind()).isEqualTo(IngestGroupKind.ALBUM);
        assertThat(created.name()).isEqualTo("Parachutes");
        assertThat(created.tracks())
                .extracting(TrackIngestResponse::originalFileName)
                .containsExactly("01 Don't Panic.flac", "02 Shiver.flac");
        assertThat(created.tracks()).extracting(TrackIngestResponse::state).containsOnly(IngestState.CREATED);
        assertThat(created.tracks())
                .extracting(TrackIngestResponse::groupIngestId)
                .containsOnly(created.groupIngestId());
        assertThat(ingestDao.loadGroupById(created.groupIngestId()))
                .contains(new IngestGroup(
                        created.groupIngestId(),
                        IngestGroupKind.ALBUM,
                        "Parachutes",
                        admin.userId(),
                        IngestGroupState.CREATED));
        for (final var track : created.tracks()) {
            assertThat(ingestDao.loadTrackIngestById(track.trackIngestId()).state())
                    .isEqualTo(IngestState.CREATED);
        }
    }

    @Test
    void eachGroupGetsItsOwnId() {
        final var request = request("Parachutes", "01 Don't Panic.flac");

        final var first = createGroup(request);
        final var second = createGroup(request);

        assertThat(first.groupIngestId()).isNotEqualTo(second.groupIngestId());
        assertThat(first.tracks().getFirst().trackIngestId())
                .isNotEqualTo(second.tracks().getFirst().trackIngestId());
    }

    @Test
    void aGroupWithoutANameIsRefused() {
        api.createIngestGroup(adminToken, request(" ", "song.flac"))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    @Test
    void aGroupWithoutFilesIsRefused() {
        api.createIngestGroup(adminToken, new IngestGroupRequest(IngestGroupKind.ALBUM, "Parachutes", List.of()))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    @Test
    void aGroupWithAFileWithoutANameIsRefused() {
        api.createIngestGroup(adminToken, request("Parachutes", "song.flac", " "))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    @Test
    void aGroupOfAnUnknownKindIsRefused() {
        api.postJson(
                        adminToken,
                        ApiRoutes.INGEST_GROUPS,
                        Map.of("kind", "PLAYLIST", "name", "Mix", "filenames", List.of("song.flac")))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    @Test
    void aGroupWithAFileInAFormatSonaCannotIngestIsRefusedAndNotStored() {
        api.createIngestGroup(adminToken, request("Parachutes", "song.flac", "song.ogg"))
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(303)
                .jsonPath("$.reason")
                .isEqualTo("Sona can't ingest files in this format yet.");
        assertThat(dsl.fetchCount(INGEST_GROUP)).isZero();
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    @Test
    void anUploadedFileWaitsToBeMatched() throws IOException {
        final var group = createGroup(request("Parachutes", "09 A Whisper.flac"));
        final var track = group.tracks().getFirst();
        final var flac = flacFile(new RawMetadata(Duration.ofSeconds(10)));

        final var response = uploadTrack(group, track, flac)
                .expectStatus()
                .isOk()
                .expectBody(TrackIngestResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response.groupIngestId()).isEqualTo(group.groupIngestId());
        assertThat(response.originalFileName()).isEqualTo("09 A Whisper.flac");
        assertThat(response.state()).isEqualTo(IngestState.UPLOADED);
        final var ingest = ingestDao.loadTrackIngestById(track.trackIngestId());
        assertThat(ingest.state()).isEqualTo(IngestState.UPLOADED);
        try (final var stored = library.readIngestTrack(ingest)) {
            assertThat(Channels.newInputStream(stored).readAllBytes()).isEqualTo(flac);
        }
    }

    @Test
    void aGroupIsUploadedOnceAllItsFilesAre() {
        final var group = createGroup(request("Parachutes", "01 Don't Panic.flac", "02 Shiver.flac"));

        uploadTrack(group, group.tracks().getFirst(), new byte[] {1, 2, 3})
                .expectStatus()
                .isOk();
        assertThat(groupState(group)).isEqualTo(IngestGroupState.CREATED);

        uploadTrack(group, group.tracks().getLast(), new byte[] {1, 2, 3})
                .expectStatus()
                .isOk();
        assertThat(groupState(group)).isEqualTo(IngestGroupState.UPLOADED);
    }

    @Test
    void aFileThatFailedToUploadMayBeSentAgain() {
        final var group = createGroup(request("Parachutes", "song.flac"));
        final var track = group.tracks().getFirst();
        // One byte over the 10MB the tests' library accepts.
        uploadTrack(group, track, new byte[10 * 1024 * 1024 + 1]).expectStatus().isEqualTo(413);
        assertThat(trackState(track)).isEqualTo(IngestState.UPLOADING);
        assertThat(groupState(group)).isEqualTo(IngestGroupState.CREATED);

        uploadTrack(group, track, new byte[] {1, 2, 3}).expectStatus().isOk();

        assertThat(trackState(track)).isEqualTo(IngestState.UPLOADED);
        assertThat(groupState(group)).isEqualTo(IngestGroupState.UPLOADED);
    }

    @Test
    void aFileThatIsUploadedAlreadyIsRefused() {
        final var group = createGroup(request("Parachutes", "song.flac"));
        final var track = group.tracks().getFirst();
        uploadTrack(group, track, new byte[] {1, 2, 3}).expectStatus().isOk();

        uploadTrack(group, track, new byte[] {4, 5, 6})
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(310);
        assertThat(trackState(track)).isEqualTo(IngestState.UPLOADED);
    }

    @Test
    void aFileForAGroupThatWasNeverCreatedIsRefused() {
        api.uploadTrack(adminToken, uuidv7(), uuidv7(), new byte[] {1, 2, 3})
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(309)
                .jsonPath("$.reason")
                .isEqualTo("This file isn't in an upload group you created.");
    }

    @Test
    void aFileThatIsNotInTheGroupItIsSentToIsRefused() {
        final var group = createGroup(request("Parachutes", "song.flac"));
        final var other = createGroup(request("Other", "other.flac"));

        api.uploadTrack(
                        adminToken,
                        group.groupIngestId(),
                        other.tracks().getFirst().trackIngestId(),
                        new byte[] {1, 2, 3})
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(309);
    }

    @Test
    void aFileLargerThanSonaAcceptsIsRefusedAndNotStored() {
        final var group = createGroup(request("Parachutes", "song.flac"));
        final var track = group.tracks().getFirst();
        // One byte over the 10MB the tests' library accepts.
        final var flac = new byte[10 * 1024 * 1024 + 1];

        uploadTrack(group, track, flac)
                .expectStatus()
                .isEqualTo(413)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(305)
                .jsonPath("$.reason")
                .isEqualTo("This file is larger than Sona accepts.");
        assertThat(trackState(track)).isNotEqualTo(IngestState.UPLOADED);
    }

    @Test
    void aFileAsLargeAsSonaAcceptsIsStored() {
        final var group = createGroup(request("Parachutes", "song.flac"));
        final var flac = new byte[10 * 1024 * 1024];

        uploadTrack(group, group.tracks().getFirst(), flac).expectStatus().isOk();

        assertThat(trackState(group.tracks().getFirst())).isEqualTo(IngestState.UPLOADED);
    }

    @Test
    void aFileSentAsAFormIsRefused() {
        final var group = createGroup(request("Parachutes", "song.flac"));
        final var track = group.tracks().getFirst();

        api.uploadTrackAsForm(adminToken, group.groupIngestId(), track.trackIngestId(), new byte[] {1, 2, 3})
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(401);
        assertThat(trackState(track)).isEqualTo(IngestState.CREATED);
    }

    @Test
    void aUserMayNotUpload() {
        final var userToken = new TestTokens(encoder).accessToken(uuidv7(), "bob", UserRole.USER, SessionState.ACTIVE);
        final var group = createGroup(request("Parachutes", "song.flac"));
        final var track = group.tracks().getFirst();

        api.createIngestGroup(userToken, request("Parachutes", "song.flac"))
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(104);
        api.uploadTrack(userToken, group.groupIngestId(), track.trackIngestId(), new byte[] {1, 2, 3})
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(104);
        assertThat(dsl.fetchCount(INGEST_GROUP)).isOne();
        assertThat(trackState(track)).isEqualTo(IngestState.CREATED);
    }

    private IngestGroupRequest request(final String name, final String... filenames) {
        return new IngestGroupRequest(IngestGroupKind.ALBUM, name, List.of(filenames));
    }

    private IngestGroupResponse createGroup(final IngestGroupRequest request) {
        return api.createIngestGroup(adminToken, request)
                .expectStatus()
                .isCreated()
                .expectBody(IngestGroupResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private RestTestClient.ResponseSpec uploadTrack(
            final IngestGroupResponse group, final TrackIngestResponse track, final byte[] file) {
        return api.uploadTrack(adminToken, group.groupIngestId(), track.trackIngestId(), file);
    }

    private IngestGroupState groupState(final IngestGroupResponse group) {
        return ingestDao.loadGroupById(group.groupIngestId()).orElseThrow().state();
    }

    private IngestState trackState(final TrackIngestResponse track) {
        return ingestDao.loadTrackIngestById(track.trackIngestId()).state();
    }
}
