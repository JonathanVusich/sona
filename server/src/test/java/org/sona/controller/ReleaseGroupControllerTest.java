package org.sona.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.response.ArtistResponse;
import org.sona.controller.response.DefaultReleaseResponse;
import org.sona.controller.response.EditionResponse;
import org.sona.controller.response.LabelResponse;
import org.sona.controller.response.MediumResponse;
import org.sona.controller.response.ReleaseGroupPageResponse;
import org.sona.controller.response.ReleaseGroupResponse;
import org.sona.controller.response.ReleaseGroupSummaryResponse;
import org.sona.controller.response.TrackResponse;
import org.sona.db.ArtistDao;
import org.sona.db.LabelDao;
import org.sona.db.MediumDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.ReleaseLabelDao;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Artist;
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ReleaseGroupControllerTest {

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
    void aReleaseGroupComesWithItsArtistAndEditionsEarliestFirst() {
        final var keane = insertArtist("Keane");
        final var group = insertReleaseGroup(keane, "Hopes and Fears");
        final var island = insertLabel("Island");
        final var interscope = insertLabel("Interscope");
        final var deluxe = insertRelease(group, "Hopes and Fears (Deluxe Edition)", LocalDate.of(2014, 5, 12));
        insertTrack(insertMedia(deluxe, "CD", "CD").getFirst(), keane, "Snowed Under");
        linkLabels(deluxe, island);
        final var undated = insertRelease(group, "Hopes and Fears", null);
        insertTrack(insertMedia(undated, (String) null).getFirst(), keane, "Bend and Break");
        // Not in the library, as none of its tracks is.
        insertMedia(insertRelease(group, "Hopes and Fears (Japan)", LocalDate.of(2004, 5, 12)), "CD");
        final var original = insertRelease(group, "Hopes and Fears", LocalDate.of(2004, 5, 10));
        // None of the DVD's tracks is in the library.
        final var disc = insertMedia(original, "CD", "DVD").getFirst();
        linkLabels(original, island, interscope);
        final var track = insertTrack(disc, keane, "Somewhere Only We Know");

        final var response = api.getWithToken(ApiRoutes.RELEASE_GROUPS + "/" + group.releaseGroupId(), token)
                .expectStatus().isOk()
                .expectBody(ReleaseGroupResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isEqualTo(new ReleaseGroupResponse(group.releaseGroupId(), "Hopes and Fears",
                ReleaseGroupType.ALBUM, LocalDate.of(2004, 5, 10), artist(keane),
                // The earliest edition stands for the group, though the deluxe one was stored first.
                new DefaultReleaseResponse(original.releaseId(), List.of(new MediumResponse(1, "CD", List.of(
                        new TrackResponse(track.trackId(), "Somewhere Only We Know", 300, 1, artist(keane)))))),
                List.of(
                edition(original, 1, List.of(label(interscope), label(island)), List.of("CD", "DVD")),
                edition(deluxe, 1, List.of(label(island)), List.of("CD", "CD")),
                // A medium whose format isn't known still counts.
                edition(undated, 1, List.of(), Arrays.asList((String) null)))));
    }

    @Test
    void aGroupWhoseReleasesAreAllUndatedHasTheFirstStoredAsItsDefault() {
        final var keane = insertArtist("Keane");
        final var group = insertReleaseGroup(keane, "Hopes and Fears");
        final var first = insertRelease(group, "Hopes and Fears", null);
        insertTrack(insertMedia(first, "CD").getFirst(), keane, "Somewhere Only We Know");
        final var deluxe = insertRelease(group, "Hopes and Fears (Deluxe Edition)", null);
        insertTrack(insertMedia(deluxe, "CD").getFirst(), keane, "Snowed Under");

        final var response = loadReleaseGroup(group);

        assertThat(response.defaultRelease().releaseId()).isEqualTo(first.releaseId());
    }

    @Test
    void anEarlierReleaseWithNoTrackInTheLibraryDoesntStandForTheGroup() {
        final var keane = insertArtist("Keane");
        final var group = insertReleaseGroup(keane, "Hopes and Fears");
        insertMedia(insertRelease(group, "Hopes and Fears", LocalDate.of(2004, 5, 10)), "CD");
        final var deluxe = insertRelease(group, "Hopes and Fears (Deluxe Edition)", LocalDate.of(2014, 5, 12));
        insertTrack(insertMedia(deluxe, "CD").getFirst(), keane, "Snowed Under");

        final var response = loadReleaseGroup(group);

        assertThat(response.defaultRelease().releaseId()).isEqualTo(deluxe.releaseId());
        assertThat(response.editions()).extracting(EditionResponse::releaseId).containsExactly(deluxe.releaseId());
    }

    @Test
    void aGroupWithNoReleaseInTheLibraryIsNotFound() {
        final var group = insertReleaseGroup(insertArtist("Keane"), "Hopes and Fears");
        insertMedia(insertRelease(group, "Hopes and Fears", LocalDate.of(2004, 5, 10)), "CD");

        api.getWithToken(ApiRoutes.RELEASE_GROUPS + "/" + group.releaseGroupId(), token)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(301);
    }

    @Test
    void anUnknownReleaseGroupIsNotFound() {
        api.getWithToken(ApiRoutes.RELEASE_GROUPS + "/" + uuidv7(), token)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(301)
                .jsonPath("$.reason").isEqualTo("This release group isn't in the library.");
    }

    @Test
    void releaseGroupsComeByNameAPageAtATime() {
        final var coldplay = insertArtist("Coldplay");
        final var keane = insertArtist("Keane");
        final var parachutes = insertReleaseGroupWithRelease(coldplay, "Parachutes");
        final var aRush = insertReleaseGroupWithRelease(coldplay, "A Rush of Blood to the Head");
        final var hopesAndFears = insertReleaseGroupWithRelease(keane, "Hopes and Fears");

        final var first = loadPage(ApiRoutes.RELEASE_GROUPS + "?limit=2");
        final var second = loadPage(ApiRoutes.RELEASE_GROUPS + "?limit=2&after=" + first.next());

        assertThat(first).isEqualTo(new ReleaseGroupPageResponse(
                List.of(summary(aRush, coldplay), summary(hopesAndFears, keane)), hopesAndFears.releaseGroupId()));
        assertThat(second).isEqualTo(new ReleaseGroupPageResponse(List.of(summary(parachutes, coldplay)), null));
    }

    @Test
    void releaseGroupsWithTheSameNameArePagedById() {
        final var queen = insertArtist("Queen");
        final var abba = insertArtist("ABBA");
        final var queensHits = insertReleaseGroupWithRelease(queen, "Greatest Hits");
        final var abbasHits = insertReleaseGroupWithRelease(abba, "Greatest Hits");

        final var first = loadPage(ApiRoutes.RELEASE_GROUPS + "?limit=1");
        final var second = loadPage(ApiRoutes.RELEASE_GROUPS + "?limit=1&after=" + first.next());

        assertThat(first.releaseGroups()).containsExactly(summary(queensHits, queen));
        assertThat(second).isEqualTo(new ReleaseGroupPageResponse(List.of(summary(abbasHits, abba)), null));
    }

    @Test
    void releaseGroupsWithNoReleaseInTheLibraryArentListed() {
        final var coldplay = insertArtist("Coldplay");
        final var parachutes = insertReleaseGroupWithRelease(coldplay, "Parachutes");
        insertReleaseGroup(coldplay, "X&Y");
        insertRelease(insertReleaseGroup(coldplay, "Viva la Vida"), "Viva la Vida", LocalDate.of(2008, 6, 12));

        assertThat(loadPage(ApiRoutes.RELEASE_GROUPS)).isEqualTo(new ReleaseGroupPageResponse(
                List.of(summary(parachutes, coldplay)), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "many"})
    void aLimitThatIsntOneToAHundredIsRefused(final String limit) {
        api.getWithToken(ApiRoutes.RELEASE_GROUPS + "?limit=" + limit, token)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    private ReleaseGroupResponse loadReleaseGroup(final ReleaseGroup group) {
        return api.getWithToken(ApiRoutes.RELEASE_GROUPS + "/" + group.releaseGroupId(), token)
                .expectStatus().isOk()
                .expectBody(ReleaseGroupResponse.class)
                .returnResult().getResponseBody();
    }

    private ReleaseGroupPageResponse loadPage(final String uri) {
        return api.getWithToken(uri, token)
                .expectStatus().isOk()
                .expectBody(ReleaseGroupPageResponse.class)
                .returnResult().getResponseBody();
    }

    private Artist insertArtist(final String name) {
        return artistDao.upsert(new Artist(uuidv7(), name, "GB", null, 0, uuidv7()));
    }

    private ReleaseGroup insertReleaseGroup(final Artist artist, final String name) {
        return releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), name, ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10), artist.artistId(), 0, uuidv7()));
    }

    /**
     * @return a release group in the library, with a release that has a track
     */
    private ReleaseGroup insertReleaseGroupWithRelease(final Artist artist, final String name) {
        final var group = insertReleaseGroup(artist, name);
        final var release = insertRelease(group, name, group.firstReleaseDate());
        insertTrack(insertMedia(release, "CD").getFirst(), artist, name);
        return group;
    }

    private Release insertRelease(final ReleaseGroup group, final String name, final LocalDate date) {
        return releaseDao.upsert(new Release(uuidv7(), name, null, date, "GB", 0, group.releaseGroupId(),
                group.artistId(), uuidv7()));
    }

    private Label insertLabel(final String name) {
        return labelDao.upsertAll(List.of(new Label(uuidv7(), name, uuidv7()))).getFirst();
    }

    private void linkLabels(final Release release, final Label... labels) {
        releaseLabelDao.upsertAll(Arrays.stream(labels)
                .map(label -> new ReleaseLabel(release.releaseId(), label.labelId()))
                .toList());
    }

    private List<Medium> insertMedia(final Release release, final String... formats) {
        final var media = IntStream.range(0, formats.length)
                .mapToObj(index -> new Medium(uuidv7(), release.releaseId(), index + 1, formats[index]))
                .toList();
        return mediumDao.upsertAll(media);
    }

    private Track insertTrack(final Medium medium, final Artist artist, final String name) {
        final var ingest = new TrackToIngest(uuidv7(), uuidv7(), name + ".flac", IngestState.COMPLETED);
        trackToIngestDao.insert(ingest);
        final var track = new Track(uuidv7(), name, 300, AudioFormat.FLAC, name + ".flac", medium.mediumId(), 1,
                artist.artistId(), ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }

    private static EditionResponse edition(final Release release, final int trackCount,
                                           final List<LabelResponse> labels, final List<String> formats) {
        return new EditionResponse(release.releaseId(), release.name(), release.date(), release.country(), trackCount,
                labels, formats);
    }

    private static ReleaseGroupSummaryResponse summary(final ReleaseGroup group, final Artist artist) {
        return new ReleaseGroupSummaryResponse(group.releaseGroupId(), group.name(), group.type(),
                group.firstReleaseDate(), artist(artist));
    }

    private static ArtistResponse artist(final Artist artist) {
        return new ArtistResponse(artist.artistId(), artist.name());
    }

    private static LabelResponse label(final Label label) {
        return new LabelResponse(label.labelId(), label.name());
    }
}
