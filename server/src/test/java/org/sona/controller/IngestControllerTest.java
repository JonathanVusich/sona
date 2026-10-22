package org.sona.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sona.model.Tables.TRACK_TO_INGEST;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.sona.db.IngestGroupDao;
import org.sona.db.TrackToIngestDao;
import org.sona.library.Library;
import org.sona.metadata.RawMetadata;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.IngestGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.util.UriComponentsBuilder;

@IntegrationTest
class IngestControllerTest {

    @Autowired
    JwtEncoder encoder;

    @Autowired
    IngestGroupDao ingestGroupDao;

    @Autowired
    TrackToIngestDao trackToIngestDao;

    @Autowired
    Library library;
    // For rows the DAOs have no lookup for.
    @Autowired
    DSLContext dsl;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    String adminToken;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        adminToken = new TestTokens(encoder).accessToken(uuidv7(), "alice", UserRole.ADMIN, SessionState.ACTIVE);
    }

    @Test
    void aGroupIsCreatedUnderANewId() {
        final var created = api.createIngestGroup(
                        adminToken, new IngestGroupRequest(IngestGroupKind.ALBUM, "Parachutes"))
                .expectStatus()
                .isCreated()
                .expectBody(IngestGroupResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created.kind()).isEqualTo(IngestGroupKind.ALBUM);
        assertThat(created.name()).isEqualTo("Parachutes");
        assertThat(ingestGroupDao.loadById(created.groupIngestId()))
                .contains(new IngestGroup(created.groupIngestId(), IngestGroupKind.ALBUM, "Parachutes"));
    }

    @Test
    void eachGroupGetsItsOwnId() {
        final var request = new IngestGroupRequest(IngestGroupKind.ALBUM, "Parachutes");

        final var first = createGroup(request);
        final var second = createGroup(request);

        assertThat(first.groupIngestId()).isNotEqualTo(second.groupIngestId());
    }

    @Test
    void aGroupWithoutANameIsRefused() {
        api.createIngestGroup(adminToken, new IngestGroupRequest(IngestGroupKind.UNKNOWN, " "))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    @Test
    void aGroupOfAnUnknownKindIsRefused() {
        api.postJson(adminToken, ApiRoutes.INGEST_GROUPS, Map.of("kind", "PLAYLIST", "name", "Mix"))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    @Test
    void anUploadedFileWaitsToBeIngested() throws IOException {
        final var group = storeGroup();
        final var flac = flacFile(new RawMetadata(Duration.ofSeconds(10)));

        final var response = api.uploadTrack(adminToken, group.ingestGroupId(), "09 A Whisper.flac", flac)
                .expectStatus()
                .isCreated()
                .expectBody(TrackIngestResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response.groupIngestId()).isEqualTo(group.ingestGroupId());
        assertThat(response.originalFileName()).isEqualTo("09 A Whisper.flac");
        assertThat(response.state()).isEqualTo(IngestState.PENDING);
        final var ingest = trackToIngestDao.loadById(response.trackIngestId());
        assertThat(ingest.state()).isEqualTo(IngestState.PENDING);
        try (final var stored = library.readIngestTrack(ingest)) {
            assertThat(stored.readAllBytes()).isEqualTo(flac);
        }
    }

    @Test
    void aFileForAGroupThatWasNeverCreatedIsRefused() {
        api.uploadTrack(adminToken, uuidv7(), "song.flac", new byte[] {1, 2, 3})
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(304)
                .jsonPath("$.reason")
                .isEqualTo("This upload group doesn't exist. Create it before uploading its files.");
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    @Test
    void aFileInAFormatSonaCannotIngestIsRefused() {
        final var group = storeGroup();

        api.uploadTrack(adminToken, group.ingestGroupId(), "song.mp3", new byte[] {1, 2, 3})
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(303)
                .jsonPath("$.reason")
                .isEqualTo("Sona can't ingest files in this format yet.");
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    @Test
    void aLargeFileInAFormatSonaCannotIngestIsRefusedWithItsReason() {
        final var group = storeGroup();
        // Larger than the 2MB of an unread body Tomcat reads by default before closing the connection.
        final var mp3 = new byte[30 * 1024 * 1024];

        api.uploadTrack(adminToken, group.ingestGroupId(), "song.mp3", mp3)
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(303);
    }

    @Test
    void aRefusedFileIsOnlyAnsweredOnceItIsAllSent() throws IOException {
        final var group = storeGroup();

        final var answer = sendInTwoHalves(group, "Authorization: Bearer " + adminToken + "\r\n");

        assertThat(answer).startsWith("HTTP/1.1 415");
    }

    @Test
    void aFileRefusedBeforeItReachesSonaIsOnlyAnsweredOnceItIsAllSent() throws IOException {
        final var group = storeGroup();

        final var answer = sendInTwoHalves(group, "");

        assertThat(answer).startsWith("HTTP/1.1 401");
    }

    @Test
    void aFileLargerThanSonaAcceptsIsRefusedAndNotStored() {
        final var group = storeGroup();
        // One byte over the 10MB the tests' library accepts.
        final var flac = new byte[10 * 1024 * 1024 + 1];

        api.uploadTrack(adminToken, group.ingestGroupId(), "song.flac", flac)
                .expectStatus()
                .isEqualTo(413)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(305)
                .jsonPath("$.reason")
                .isEqualTo("This file is larger than Sona accepts.");
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    @Test
    void aFileAsLargeAsSonaAcceptsIsStored() {
        final var group = storeGroup();
        final var flac = new byte[10 * 1024 * 1024];

        api.uploadTrack(adminToken, group.ingestGroupId(), "song.flac", flac)
                .expectStatus()
                .isCreated();
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isOne();
    }

    @Test
    void aFileSentAsAFormIsRefused() {
        final var group = storeGroup();

        api.uploadTrackAsForm(adminToken, group.ingestGroupId(), "song.flac", new byte[] {1, 2, 3})
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(401);
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    @Test
    void aFileWithoutANameIsRefused() {
        final var group = storeGroup();

        api.uploadTrackWithoutFilename(adminToken, group.ingestGroupId(), new byte[] {1, 2, 3})
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    @Test
    void aUserMayNotUpload() {
        final var userToken = new TestTokens(encoder).accessToken(uuidv7(), "bob", UserRole.USER, SessionState.ACTIVE);
        final var group = storeGroup();

        api.createIngestGroup(userToken, new IngestGroupRequest(IngestGroupKind.ALBUM, "Parachutes"))
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(104);
        api.uploadTrack(userToken, group.ingestGroupId(), "song.flac", new byte[] {1, 2, 3})
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(104);
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    /**
     * Uploads an MP3, which Sona refuses before reading it, over a socket of its own, so the test sees when the answer
     * arrives: none may come while half of the file is still to be sent, since a proxy in front of Sona can close the
     * connection once the answer is through, and the client then never gets it.
     *
     * @return the start of the answer
     */
    private String sendInTwoHalves(final IngestGroup group, final String authorizationHeader) throws IOException {
        final var half = new byte[1024 * 1024];
        final var path = UriComponentsBuilder.fromPath(ApiRoutes.INGEST_GROUP_TRACKS)
                .queryParam("filename", "song.mp3")
                .buildAndExpand(group.ingestGroupId())
                .toUriString();
        final var head = "POST " + path + " HTTP/1.1\r\nHost: localhost\r\n" + authorizationHeader
                + "Content-Type: application/octet-stream\r\nContent-Length: " + 2 * half.length + "\r\n\r\n";
        try (final var socket = new Socket("localhost", port)) {
            final var out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(half);
            out.flush();
            socket.setSoTimeout(500);
            assertThatThrownBy(() -> socket.getInputStream().read()).isInstanceOf(SocketTimeoutException.class);
            out.write(half);
            out.flush();
            socket.setSoTimeout(5000);
            return new String(socket.getInputStream().readNBytes(12), StandardCharsets.US_ASCII);
        }
    }

    private IngestGroupResponse createGroup(final IngestGroupRequest request) {
        return api.createIngestGroup(adminToken, request)
                .expectStatus()
                .isCreated()
                .expectBody(IngestGroupResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private IngestGroup storeGroup() {
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "A Rush of Blood to the Head");
        ingestGroupDao.insert(group);
        return group;
    }
}
