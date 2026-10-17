package org.sona.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.response.ArtistDetailsResponse;
import org.sona.controller.response.ArtistPageResponse;
import org.sona.controller.response.ArtistReleaseGroupResponse;
import org.sona.controller.response.ArtistSummaryResponse;
import org.sona.db.ArtistDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ArtistControllerTest {

    @Autowired
    JwtEncoder encoder;
    @Autowired
    ArtistDao artistDao;
    @Autowired
    ReleaseGroupDao releaseGroupDao;
    @Autowired
    ReleaseDao releaseDao;

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
    void anArtistComesWithTheirReleaseGroupsNewestFirst() {
        final var coldplay = insertArtist("Coldplay", "GB");
        final var parachutes = insertReleaseGroup(coldplay, "Parachutes", ReleaseGroupType.ALBUM,
                LocalDate.of(2000, 7, 10));
        final var demos = insertReleaseGroup(coldplay, "Demos", null, null);
        final var yellow = insertReleaseGroup(coldplay, "Yellow", ReleaseGroupType.SINGLE, LocalDate.of(2000, 6, 26));
        final var aRush = insertReleaseGroup(coldplay, "A Rush of Blood to the Head", ReleaseGroupType.ALBUM,
                LocalDate.of(2002, 8, 26));
        insertReleaseGroup(insertArtist("Keane", "GB"), "Hopes and Fears", ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10));

        final var response = api.getWithToken(ApiRoutes.ARTISTS + "/" + coldplay.artistId(), token)
                .expectStatus().isOk()
                .expectBody(ArtistDetailsResponse.class)
                .returnResult().getResponseBody();

        // Those with no known date come last.
        assertThat(response).isEqualTo(new ArtistDetailsResponse(coldplay.artistId(), "Coldplay", "GB", List.of(
                releaseGroup(aRush), releaseGroup(parachutes), releaseGroup(yellow), releaseGroup(demos))));
    }

    @Test
    void anArtistsReleaseGroupsWithNoReleaseArentInTheLibrary() {
        final var coldplay = insertArtist("Coldplay", "GB");
        final var parachutes = insertReleaseGroup(coldplay, "Parachutes", ReleaseGroupType.ALBUM,
                LocalDate.of(2000, 7, 10));
        insertReleaseGroupWithoutRelease(coldplay, "X&Y", ReleaseGroupType.ALBUM, LocalDate.of(2005, 6, 6));

        final var response = api.getWithToken(ApiRoutes.ARTISTS + "/" + coldplay.artistId(), token)
                .expectStatus().isOk()
                .expectBody(ArtistDetailsResponse.class)
                .returnResult().getResponseBody();

        assertThat(response.releaseGroups()).containsExactly(releaseGroup(parachutes));
    }

    @Test
    void anArtistWithNoReleaseGroupsHasNone() {
        final var artist = insertArtist("Ada Mori", null);

        final var response = api.getWithToken(ApiRoutes.ARTISTS + "/" + artist.artistId(), token)
                .expectStatus().isOk()
                .expectBody(ArtistDetailsResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isEqualTo(new ArtistDetailsResponse(artist.artistId(), "Ada Mori", null, List.of()));
    }

    @Test
    void anUnknownArtistIsNotFound() {
        api.getWithToken(ApiRoutes.ARTISTS + "/" + uuidv7(), token)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(302)
                .jsonPath("$.reason").isEqualTo("This artist isn't in the library.");
    }

    @Test
    void artistsComeByNameAPageAtATimeWithHowManyReleaseGroupsTheyHave() {
        final var keane = insertArtist("Keane", "GB");
        final var blur = insertArtist("Blur", "GB");
        final var coldplay = insertArtist("Coldplay", "GB");
        insertReleaseGroup(coldplay, "Parachutes", ReleaseGroupType.ALBUM, LocalDate.of(2000, 7, 10));
        insertReleaseGroup(coldplay, "Yellow", ReleaseGroupType.SINGLE, LocalDate.of(2000, 6, 26));
        insertReleaseGroup(keane, "Hopes and Fears", ReleaseGroupType.ALBUM, LocalDate.of(2004, 5, 10));
        insertReleaseGroup(blur, "Parklife", ReleaseGroupType.ALBUM, LocalDate.of(1994, 4, 25));

        final var first = loadPage(ApiRoutes.ARTISTS + "?limit=2");
        final var second = loadPage(ApiRoutes.ARTISTS + "?limit=2&after=" + first.next());

        assertThat(first).isEqualTo(new ArtistPageResponse(
                List.of(summary(blur, 1), summary(coldplay, 2)), coldplay.artistId()));
        assertThat(second).isEqualTo(new ArtistPageResponse(List.of(summary(keane, 1)), null));
    }

    @Test
    void artistsWithNoReleaseGroupsArentListed() {
        final var coldplay = insertArtist("Coldplay", "GB");
        insertArtist("Ada Mori", null);
        insertReleaseGroup(coldplay, "Parachutes", ReleaseGroupType.ALBUM, LocalDate.of(2000, 7, 10));

        assertThat(loadPage(ApiRoutes.ARTISTS)).isEqualTo(new ArtistPageResponse(List.of(summary(coldplay, 1)),
                null));
    }

    @Test
    void artistsWhoseReleaseGroupsHaveNoReleaseArentListed() {
        final var coldplay = insertArtist("Coldplay", "GB");
        final var keane = insertArtist("Keane", "GB");
        insertReleaseGroup(coldplay, "Parachutes", ReleaseGroupType.ALBUM, LocalDate.of(2000, 7, 10));
        insertReleaseGroupWithoutRelease(coldplay, "X&Y", ReleaseGroupType.ALBUM, LocalDate.of(2005, 6, 6));
        insertReleaseGroupWithoutRelease(keane, "Hopes and Fears", ReleaseGroupType.ALBUM, LocalDate.of(2004, 5, 10));

        assertThat(loadPage(ApiRoutes.ARTISTS)).isEqualTo(new ArtistPageResponse(List.of(summary(coldplay, 1)),
                null));
    }

    @Test
    void artistsWithTheSameNameArePagedById() {
        final var american = insertArtist("Nirvana", "US");
        final var british = insertArtist("Nirvana", "GB");
        insertReleaseGroup(american, "Nevermind", ReleaseGroupType.ALBUM, LocalDate.of(1991, 9, 24));
        insertReleaseGroup(british, "Local Anaesthetic", ReleaseGroupType.ALBUM, LocalDate.of(1971, 1, 1));

        final var first = loadPage(ApiRoutes.ARTISTS + "?limit=1");
        final var second = loadPage(ApiRoutes.ARTISTS + "?limit=1&after=" + first.next());

        assertThat(first.artists()).containsExactly(summary(american, 1));
        assertThat(second).isEqualTo(new ArtistPageResponse(List.of(summary(british, 1)), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "many"})
    void aLimitThatIsntOneToAHundredIsRefused(final String limit) {
        api.getWithToken(ApiRoutes.ARTISTS + "?limit=" + limit, token)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    private ArtistPageResponse loadPage(final String uri) {
        return api.getWithToken(uri, token)
                .expectStatus().isOk()
                .expectBody(ArtistPageResponse.class)
                .returnResult().getResponseBody();
    }

    private Artist insertArtist(final String name, final String country) {
        return artistDao.upsert(new Artist(uuidv7(), name, country, null, 0, uuidv7()));
    }

    /**
     * @return a release group in the library, with a release stored
     */
    private ReleaseGroup insertReleaseGroup(final Artist artist, final String name, final ReleaseGroupType type,
                                            final LocalDate firstReleaseDate) {
        final var group = insertReleaseGroupWithoutRelease(artist, name, type, firstReleaseDate);
        releaseDao.upsert(new Release(uuidv7(), name, null, firstReleaseDate, "GB", 0, group.releaseGroupId(),
                artist.artistId(), uuidv7()));
        return group;
    }

    private ReleaseGroup insertReleaseGroupWithoutRelease(final Artist artist, final String name,
                                                          final ReleaseGroupType type,
                                                          final LocalDate firstReleaseDate) {
        return releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), name, type, firstReleaseDate, artist.artistId(), 0,
                uuidv7()));
    }

    private static ArtistReleaseGroupResponse releaseGroup(final ReleaseGroup group) {
        return new ArtistReleaseGroupResponse(group.releaseGroupId(), group.name(), group.type(),
                group.firstReleaseDate());
    }

    private static ArtistSummaryResponse summary(final Artist artist, final int releaseGroupCount) {
        return new ArtistSummaryResponse(artist.artistId(), artist.name(), releaseGroupCount);
    }
}
