package org.sona.controller;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.request.IngestGroupRequest;
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

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.model.Tables.TRACK_TO_INGEST;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

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
    void aGroupIsCreatedUnderTheIdTheUploaderMadeUp() {
        final var groupIngestId = uuidv7();

        api.putIngestGroup(adminToken, groupIngestId, new IngestGroupRequest(IngestGroupKind.ALBUM, "Parachutes"))
                .expectStatus().isNoContent();

        assertThat(ingestGroupDao.loadById(groupIngestId))
                .contains(new IngestGroup(groupIngestId, IngestGroupKind.ALBUM, "Parachutes"));
    }

    @Test
    void aGroupSentAgainIsUpdated() {
        final var groupIngestId = uuidv7();
        api.putIngestGroup(adminToken, groupIngestId, new IngestGroupRequest(IngestGroupKind.ALBUM, "Parachutes"))
                .expectStatus().isNoContent();

        api.putIngestGroup(adminToken, groupIngestId, new IngestGroupRequest(IngestGroupKind.UNKNOWN, "Loose tracks"))
                .expectStatus().isNoContent();

        assertThat(ingestGroupDao.loadById(groupIngestId))
                .contains(new IngestGroup(groupIngestId, IngestGroupKind.UNKNOWN, "Loose tracks"));
    }

    @Test
    void aGroupWithoutANameIsRefused() {
        api.putIngestGroup(adminToken, uuidv7(), new IngestGroupRequest(IngestGroupKind.UNKNOWN, " "))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    @Test
    void aGroupOfAnUnknownKindIsRefused() {
        api.putJson(adminToken, ApiRoutes.INGEST_GROUPS + "/" + uuidv7(), Map.of("kind", "PLAYLIST", "name", "Mix"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    @Test
    void anUploadedFileWaitsToBeIngested() throws IOException {
        final var group = storeGroup();
        final var flac = flacFile(new RawMetadata(Duration.ofSeconds(10)));

        final var response = api.uploadTrack(adminToken, group.ingestGroupId(), "09 A Whisper.flac", flac)
                .expectStatus().isCreated()
                .expectBody(TrackIngestResponse.class)
                .returnResult().getResponseBody();

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
        api.uploadTrack(adminToken, uuidv7(), "song.flac", new byte[]{1, 2, 3})
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(304)
                .jsonPath("$.reason")
                .isEqualTo("This upload group doesn't exist. Create it before uploading its files.");
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    @Test
    void aFileInAFormatSonaCannotIngestIsRefused() {
        final var group = storeGroup();

        api.uploadTrack(adminToken, group.ingestGroupId(), "song.mp3", new byte[]{1, 2, 3})
                .expectStatus().isEqualTo(415)
                .expectBody()
                .jsonPath("$.code").isEqualTo(303)
                .jsonPath("$.reason").isEqualTo("Sona can't ingest files in this format yet.");
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    @Test
    void aFileSentAsAFormIsRefused() {
        final var group = storeGroup();

        api.uploadTrackAsForm(adminToken, group.ingestGroupId(), "song.flac", new byte[]{1, 2, 3})
                .expectStatus().isEqualTo(415)
                .expectBody()
                .jsonPath("$.code").isEqualTo(401);
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    @Test
    void aFileWithoutANameIsRefused() {
        final var group = storeGroup();

        api.uploadTrackWithoutFilename(adminToken, group.ingestGroupId(), new byte[]{1, 2, 3})
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    @Test
    void aUserMayNotUpload() {
        final var userToken = new TestTokens(encoder).accessToken(uuidv7(), "bob", UserRole.USER,
                SessionState.ACTIVE);
        final var group = storeGroup();

        api.putIngestGroup(userToken, uuidv7(), new IngestGroupRequest(IngestGroupKind.ALBUM, "Parachutes"))
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo(104);
        api.uploadTrack(userToken, group.ingestGroupId(), "song.flac", new byte[]{1, 2, 3})
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo(104);
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    private IngestGroup storeGroup() {
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "A Rush of Blood to the Head");
        ingestGroupDao.upsert(group);
        return group;
    }
}
