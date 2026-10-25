package org.sona.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.config.properties.LibraryProperties;
import org.sona.controller.response.StreamTokenResponse;
import org.sona.db.ArtistDao;
import org.sona.db.IngestGroupDao;
import org.sona.db.MediumDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
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
import org.sona.samples.FlacAudioSample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@IntegrationTest
class TrackControllerTest {

    @Autowired
    JwtEncoder encoder;

    @Autowired
    LibraryProperties libraryProperties;

    @Autowired
    ArtistDao artistDao;

    @Autowired
    ReleaseGroupDao releaseGroupDao;

    @Autowired
    ReleaseDao releaseDao;

    @Autowired
    MediumDao mediumDao;

    @Autowired
    TrackDao trackDao;

    @Autowired
    IngestGroupDao ingestGroupDao;

    @Autowired
    UserDao userDao;

    @Autowired
    TrackToIngestDao trackToIngestDao;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    TestTokens tokens;
    String accessToken;
    byte[] audio;

    @BeforeEach
    void setUp() throws IOException {
        api = new ApiClient(port);
        tokens = new TestTokens(encoder);
        accessToken = tokens.accessToken(uuidv7(), "alice", UserRole.USER, SessionState.ACTIVE);
        try (final var sample = FlacAudioSample.SILENCE_10_SECONDS.inputStream()) {
            audio = sample.readAllBytes();
        }
    }

    @Test
    void aStreamTokenStreamsTheTracksFileAsItsStored() throws IOException {
        final var track = insertTrack(IngestState.COMPLETED);
        final var streamToken = streamToken(track.trackId());

        final var streamed = api.streamTrack(track.trackId(), streamToken)
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.parseMediaType("audio/flac"))
                .expectHeader()
                .valueEquals(HttpHeaders.ACCEPT_RANGES, "bytes")
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertThat(streamed).isEqualTo(audio);
    }

    @Test
    void aRangeRequestStreamsOnlyThatPartOfTheFile() throws IOException {
        final var track = insertTrack(IngestState.COMPLETED);
        final var streamToken = streamToken(track.trackId());

        final var streamed = api.streamTrackRange(track.trackId(), streamToken, "bytes=100-199")
                .expectStatus()
                .isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectHeader()
                .valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 100-199/" + audio.length)
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertThat(streamed).isEqualTo(Arrays.copyOfRange(audio, 100, 200));
    }

    @Test
    void aStreamTokenOnlyStreamsTheTrackItWasIssuedFor() throws IOException {
        final var track = insertTrack(IngestState.COMPLETED);
        final var otherTrack = insertTrack(IngestState.COMPLETED);
        final var streamToken = streamToken(otherTrack.trackId());

        api.streamTrack(track.trackId(), streamToken)
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(104);
    }

    @Test
    void aStreamNeedsAStreamToken() throws IOException {
        final var track = insertTrack(IngestState.COMPLETED);

        api.getAnonymously(ApiRoutes.TRACKS + "/" + track.trackId() + "/stream")
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(100);
    }

    @Test
    void anAccessTokenDoesNotStream() throws IOException {
        final var track = insertTrack(IngestState.COMPLETED);

        api.streamTrack(track.trackId(), accessToken).expectStatus().isUnauthorized();
    }

    @Test
    void aStreamTokenIsNotAnAccessToken() throws IOException {
        final var track = insertTrack(IngestState.COMPLETED);
        final var streamToken = streamToken(track.trackId());

        api.getWithToken(ApiRoutes.TRACKS + "/" + track.trackId() + "/stream-token", streamToken)
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void aTrackWhoseIngestHasNotCompletedIsNotStreamed() throws IOException {
        final var track = insertTrack(IngestState.MOVE_FAILED);
        final var streamToken = streamToken(track.trackId());

        api.streamTrack(track.trackId(), streamToken)
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(306)
                .jsonPath("$.reason")
                .isEqualTo("This track isn't in the library.");
    }

    @Test
    void aUserWhoMustChangeTheirPasswordGetsNoStreamToken() {
        final var mustChange =
                tokens.accessToken(uuidv7(), "bob", UserRole.USER, SessionState.PASSWORD_CHANGE_REQUIRED);

        api.getWithToken(ApiRoutes.TRACKS + "/" + uuidv7() + "/stream-token", mustChange)
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(103);
    }

    private String streamToken(final UUID trackId) {
        return api.getWithToken(ApiRoutes.TRACKS + "/" + trackId + "/stream-token", accessToken)
                .expectStatus()
                .isOk()
                .expectBody(StreamTokenResponse.class)
                .returnResult()
                .getResponseBody()
                .token();
    }

    private Track insertTrack(final IngestState state) throws IOException {
        final var artist = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var releaseGroup = releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), "Parachutes", null, null, artist.artistId(), 0, uuidv7()));
        final var release = releaseDao.upsert(new Release(
                uuidv7(),
                "Parachutes",
                null,
                LocalDate.of(2000, 7, 10),
                "GB",
                0,
                releaseGroup.releaseGroupId(),
                artist.artistId(),
                uuidv7()));
        final var medium = mediumDao
                .upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), 1, "CD")))
                .getFirst();
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId());
        ingestGroupDao.insert(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "Yellow.flac", state);
        trackToIngestDao.insert(ingest);
        final var trackId = uuidv7();
        final var path = "%s/%s/%s.flac".formatted(releaseGroup.releaseGroupId(), release.releaseId(), trackId);
        final var file = libraryProperties.mediaFolder().resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, audio);
        final var track = new Track(
                trackId,
                "Yellow",
                10,
                AudioFormat.FLAC,
                path,
                medium.mediumId(),
                1,
                artist.artistId(),
                ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }
}
