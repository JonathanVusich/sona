package org.sona.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.response.ArtistResponse;
import org.sona.controller.response.LabelResponse;
import org.sona.controller.response.MediumResponse;
import org.sona.controller.response.ReleaseResponse;
import org.sona.controller.response.TrackResponse;
import org.sona.db.ArtistDao;
import org.sona.db.IngestGroupDao;
import org.sona.db.LabelDao;
import org.sona.db.MediumDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.ReleaseLabelDao;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@IntegrationTest
class ReleaseControllerTest {

    @Autowired
    JwtEncoder encoder;

    @Autowired
    ArtistDao artistDao;

    @Autowired
    ReleaseGroupDao releaseGroupDao;

    @Autowired
    ReleaseDao releaseDao;

    @Autowired
    LabelDao labelDao;

    @Autowired
    ReleaseLabelDao releaseLabelDao;

    @Autowired
    MediumDao mediumDao;

    @Autowired
    TrackDao trackDao;

    @Autowired
    IngestGroupDao ingestGroupDao;

    @Autowired
    TrackToIngestDao trackToIngestDao;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    String token;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        token = new TestTokens(encoder).accessToken(uuidv7(), "alice", UserRole.USER, SessionState.ACTIVE);
    }

    @Test
    void aReleaseComesWithItsArtistLabelsFormatsAndMediaEachWithItsTracks() {
        final var various = artistDao.upsert(new Artist(uuidv7(), "Various Artists", null, null, 0, uuidv7()));
        final var coldplay = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var keane = artistDao.upsert(new Artist(uuidv7(), "Keane", "GB", null, 0, uuidv7()));
        final var releaseGroup = releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), "Britpop", null, null, various.artistId(), 0, uuidv7()));
        final var release = releaseDao.upsert(new Release(
                uuidv7(),
                "Britpop",
                null,
                LocalDate.of(2002, 8, 26),
                "GB",
                0,
                releaseGroup.releaseGroupId(),
                various.artistId(),
                uuidv7()));
        final var parlophone = labelDao.upsertAll(List.of(new Label(uuidv7(), "Parlophone", uuidv7())))
                .getFirst();
        releaseLabelDao.upsertAll(List.of(new ReleaseLabel(release.releaseId(), parlophone.labelId())));
        final var disc1 = mediumDao
                .upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), 1, "CD")))
                .getFirst();
        final var disc2 = mediumDao
                .upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), 2, "CD")))
                .getFirst();
        final var yellow = insertTrack(disc1, coldplay, "Yellow");
        final var somewhereOnlyWeKnow = insertTrack(disc2, keane, "Somewhere Only We Know");
        // None of its tracks is in the library.
        mediumDao.upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), 3, "DVD")));

        final var response = api.getWithToken(ApiRoutes.RELEASES + "/" + release.releaseId(), token)
                .expectStatus()
                .isOk()
                .expectBody(ReleaseResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response)
                .isEqualTo(new ReleaseResponse(
                        release.releaseId(),
                        "Britpop",
                        LocalDate.of(2002, 8, 26),
                        "GB",
                        2,
                        release.releaseGroupId(),
                        artist(various),
                        List.of(new LabelResponse(parlophone.labelId(), "Parlophone")),
                        List.of("CD", "CD", "DVD"),
                        List.of(
                                new MediumResponse(1, "CD", List.of(track(yellow, coldplay))),
                                new MediumResponse(2, "CD", List.of(track(somewhereOnlyWeKnow, keane))))));
    }

    @Test
    void anUnknownReleaseIsNotFound() {
        api.getWithToken(ApiRoutes.RELEASES + "/" + uuidv7(), token)
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(300)
                .jsonPath("$.reason")
                .isEqualTo("This release isn't in the library.");
    }

    @Test
    void anIdThatIsntAUuidIsRefused() {
        api.getWithToken(ApiRoutes.RELEASES + "/yellow", token)
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400)
                .jsonPath("$.reason")
                .isEqualTo("The request has a parameter or field that is missing or invalid.");
    }

    private Track insertTrack(final Medium medium, final Artist artist, final String name) {
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads");
        ingestGroupDao.upsert(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), name + ".flac", IngestState.COMPLETED);
        trackToIngestDao.insert(ingest);
        final var track = new Track(
                uuidv7(),
                name,
                300,
                AudioFormat.FLAC,
                name + ".flac",
                medium.mediumId(),
                1,
                artist.artistId(),
                ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }

    private static ArtistResponse artist(final Artist artist) {
        return new ArtistResponse(artist.artistId(), artist.name());
    }

    private static TrackResponse track(final Track track, final Artist artist) {
        return new TrackResponse(track.trackId(), track.name(), track.duration(), track.position(), artist(artist));
    }
}
