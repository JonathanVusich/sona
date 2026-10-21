package org.sona.controller;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.db.ReleaseDao;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.library.Library;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sona.model.Tables.TRACK_TO_INGEST;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class IngestControllerTest {

    @Autowired
    JwtEncoder encoder;
    @Autowired
    TrackToIngestDao trackToIngestDao;
    @Autowired
    TrackDao trackDao;
    @Autowired
    ReleaseDao releaseDao;
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
    void anUploadedFileIsImportedIntoTheLibrary() throws IOException {
        final var flac = flacFile(whisperMetadata());
        // The uploader makes up its group's ID.
        final var groupIngestId = uuidv7();

        final var response = api.uploadTrack(adminToken, groupIngestId, "09 A Whisper.flac", flac)
                .expectStatus().isCreated()
                .expectBody(TrackIngestResponse.class)
                .returnResult().getResponseBody();

        assertThat(response.groupIngestId()).isEqualTo(groupIngestId);
        assertThat(response.originalFileName()).isEqualTo("09 A Whisper.flac");
        assertThat(response.state()).isEqualTo(IngestState.PENDING);
        awaitState(response.trackIngestId(), IngestState.COMPLETED);
        final var track = trackDao.loadByIngest(response.trackIngestId());
        assertThat(track.name()).isEqualTo("A Whisper");
        assertThat(releaseDao.loadByMusicBrainzId(UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0")))
                .isPresent();
        try (final var imported = library.readTrack(track)) {
            assertThat(imported.readAllBytes()).isEqualTo(flac);
        }
    }

    @Test
    void anUploadedFileMusicBrainzDoesNotKnowNeedsInput() {
        final var flac = flacFile(metadata("Zzyzx Qwerty Nonexistent Song", "Nobody Atall"));

        final var ingestId = uploadTrack("song.flac", flac);

        awaitState(ingestId, IngestState.INPUT_REQUIRED);
    }

    @Test
    void anUploadedFileThatIsNotFlacFails() {
        final var ingestId = uploadTrack("song.flac", new byte[]{1, 2, 3, 4});

        awaitState(ingestId, IngestState.FAILED);
    }

    @Test
    void anUploadedFileWhoseLookupFailsFails() {
        // MusicBrainz isn't stubbed for this track, so looking it up answers 404.
        final var flac = flacFile(metadata("Unstubbed Song", "Unstubbed Artist"));

        final var ingestId = uploadTrack("song.flac", flac);

        awaitState(ingestId, IngestState.FAILED);
    }

    @Test
    void aFileInAFormatSonaCannotIngestIsRefused() {
        final var groupIngestId = uuidv7();

        api.uploadTrack(adminToken, groupIngestId, "song.mp3", new byte[]{1, 2, 3})
                .expectStatus().isEqualTo(415)
                .expectBody()
                .jsonPath("$.code").isEqualTo(303)
                .jsonPath("$.reason").isEqualTo("Sona can't ingest files in this format yet.");
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    @Test
    void aFileSentAsAFormIsRefused() {
        final var groupIngestId = uuidv7();

        api.uploadTrackAsForm(adminToken, groupIngestId, "song.flac", flacFile(whisperMetadata()))
                .expectStatus().isEqualTo(415)
                .expectBody()
                .jsonPath("$.code").isEqualTo(401);
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    @Test
    void aFileWithoutANameIsRefused() {
        final var groupIngestId = uuidv7();

        api.uploadTrackWithoutFilename(adminToken, groupIngestId, new byte[]{1, 2, 3})
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    @Test
    void aUserMayNotUpload() {
        final var userToken = new TestTokens(encoder).accessToken(uuidv7(), "bob", UserRole.USER,
                SessionState.ACTIVE);

        api.uploadTrack(userToken, uuidv7(), "song.flac", new byte[]{1, 2, 3})
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo(104);
        assertThat(dsl.fetchCount(TRACK_TO_INGEST)).isZero();
    }

    /**
     * @return the ingest of the uploaded file
     */
    private UUID uploadTrack(final String filename, final byte[] file) {
        return api.uploadTrack(adminToken, uuidv7(), filename, file)
                .expectStatus().isCreated()
                .expectBody(TrackIngestResponse.class)
                .returnResult().getResponseBody().trackIngestId();
    }

    private void awaitState(final UUID trackIngestId, final IngestState state) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(trackToIngestDao.loadById(trackIngestId).state()).isEqualTo(state));
    }

    private static RawMetadata whisperMetadata() {
        final var metadata = metadata("A Whisper", "Coldplay");
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(9));
        return metadata;
    }

    private static RawMetadata metadata(final String title, final String artist) {
        final var metadata = new RawMetadata(Duration.ofSeconds(10));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str(title));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str(artist));
        return metadata;
    }
}
