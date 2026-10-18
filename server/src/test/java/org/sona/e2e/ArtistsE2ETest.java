package org.sona.e2e;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.db.ArtistDao;
import org.sona.db.MediumDao;
import org.sona.db.PasswordDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@E2ETest
class ArtistsE2ETest {

    @Autowired
    UserDao userDao;
    @Autowired
    PasswordDao passwordDao;
    @Autowired
    PasswordEncoder encoder;
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
    TrackToIngestDao trackToIngestDao;

    @BeforeEach
    void setUp() {
        new TestUsers(userDao, passwordDao, encoder).createUser("alice", UserRole.USER);
    }

    @Test
    void artistsWithAReleaseGroupAreListedByNameWithHowManyTheyHave(final Page page) {
        final var keane = insertArtist("Keane", "GB");
        final var coldplay = insertArtist("Coldplay", "GB");
        insertArtist("Ada Mori", null);
        insertReleaseGroup(keane, "Hopes and Fears", ReleaseGroupType.ALBUM, LocalDate.of(2004, 5, 10));
        insertReleaseGroup(coldplay, "Parachutes", ReleaseGroupType.ALBUM, LocalDate.of(2000, 7, 10));
        insertReleaseGroup(coldplay, "Yellow", ReleaseGroupType.SINGLE, LocalDate.of(2000, 6, 26));

        final var artists = logInAndOpenArtists(page);

        assertThat(artists.names()).hasText(new String[]{"Coldplay", "Keane"});
        assertThat(artists.subtitle("Coldplay")).hasText("2 releases");
        assertThat(artists.subtitle("Keane")).hasText("1 release");
    }

    @Test
    void anArtistOpensTheirPage(final Page page) {
        final var keane = insertArtist("Keane", "GB");
        insertReleaseGroup(keane, "Hopes and Fears", ReleaseGroupType.ALBUM, LocalDate.of(2004, 5, 10));
        final var artists = logInAndOpenArtists(page);

        artists.artist("Keane").click();

        assertThat(page).hasURL(Pattern.compile("/artists/" + keane.artistId() + "$"));
        assertThat(new ArtistPage(page).name()).hasText("Keane");
    }

    @Test
    void moreArtistsLoadAsTheUserScrolls(final Page page) {
        // One more than a page holds.
        for (var number = 1; number <= 51; number++) {
            final var artist = insertArtist("Artist %02d".formatted(number), "GB");
            insertReleaseGroup(artist, "Album", ReleaseGroupType.ALBUM, null);
        }
        final var artists = logInAndOpenArtists(page);
        assertThat(artists.names()).hasCount(50);

        artists.scrollToEnd();

        assertThat(artists.names()).hasCount(51);
        assertThat(artists.artist("Artist 51")).isVisible();
    }

    @Test
    void anEmptyLibraryHasNoArtists(final Page page) {
        final var artists = logInAndOpenArtists(page);

        assertThat(artists.message()).hasText("No artists in the library yet.");
    }

    @Test
    void anArtistsPageListsAlbumsApartFromSinglesAndEpsNewestFirst(final Page page) {
        final var coldplay = insertArtist("Coldplay", "GB");
        insertReleaseGroup(coldplay, "Parachutes", ReleaseGroupType.ALBUM, LocalDate.of(2000, 7, 10));
        insertReleaseGroup(coldplay, "A Rush of Blood to the Head", ReleaseGroupType.ALBUM, LocalDate.of(2002, 8, 26));
        insertReleaseGroup(coldplay, "Yellow", ReleaseGroupType.SINGLE, LocalDate.of(2000, 6, 26));
        final var artistPage = logInAndOpenArtist(page, coldplay);

        assertThat(artistPage.name()).hasText("Coldplay");
        assertThat(artistPage.details()).hasText("GB · 2 albums · 1 single or EP");
        assertThat(artistPage.sectionTitles()).hasText(new String[]{"Albums", "Singles & EPs"});
        assertThat(artistPage.releaseGroupTitles("Albums"))
                .hasText(new String[]{"A Rush of Blood to the Head", "Parachutes"});
        assertThat(artistPage.releaseGroupTitles("Singles & EPs")).hasText(new String[]{"Yellow"});
    }

    @Test
    void aReleaseGroupOpensItsPage(final Page page) {
        final var keane = insertArtist("Keane", "GB");
        final var group = insertReleaseGroup(keane, "Hopes and Fears", ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10));
        final var artistPage = logInAndOpenArtist(page, keane);

        artistPage.releaseGroup("Hopes and Fears").click();

        assertThat(page).hasURL(Pattern.compile("/release-groups/" + group.releaseGroupId() + "$"));
        assertThat(new ReleaseGroupPage(page).title()).hasText("Hopes and Fears");
    }

    @Test
    void anArtistWhoIsntInTheLibraryIsReported(final Page page) {
        logIn(page);
        final var artistPage = new ArtistPage(page);

        artistPage.open(uuidv7());

        assertThat(artistPage.message()).hasText("This artist isn't in the library.");
    }

    private ArtistsPage logInAndOpenArtists(final Page page) {
        logIn(page);
        final var artists = new ArtistsPage(page);
        artists.open();
        return artists;
    }

    private ArtistPage logInAndOpenArtist(final Page page, final Artist artist) {
        logIn(page);
        final var artistPage = new ArtistPage(page);
        artistPage.open(artist.artistId());
        return artistPage;
    }

    private static void logIn(final Page page) {
        new LoginPage(page).logIn("alice", "alice");
        // Opening a page before the login finishes would reload it without a session.
        assertThat(page).hasURL(Pattern.compile("/discover$"));
    }

    private Artist insertArtist(final String name, final String country) {
        return artistDao.upsert(new Artist(uuidv7(), name, country, null, 0, uuidv7()));
    }

    /**
     * @return a release group in the library, with a release that has a track
     */
    private ReleaseGroup insertReleaseGroup(final Artist artist, final String name, final ReleaseGroupType type,
                                            final LocalDate firstReleaseDate) {
        final var group = releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), name, type, firstReleaseDate,
                artist.artistId(), 0, uuidv7()));
        final var release = releaseDao.upsert(new Release(uuidv7(), name, null, firstReleaseDate, "GB", 0,
                group.releaseGroupId(), artist.artistId(), uuidv7()));
        insertTrack(release);
        return group;
    }

    /**
     * Gives the release a track, so it's in the library.
     */
    private void insertTrack(final Release release) {
        final var medium = mediumDao.upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), 1, "CD"))).getFirst();
        final var ingest = new TrackToIngest(uuidv7(), uuidv7(), "track.flac", IngestState.COMPLETED);
        trackToIngestDao.insert(ingest);
        trackDao.insert(new Track(uuidv7(), release.name(), 300, AudioFormat.FLAC, "track.flac", medium.mediumId(), 1,
                release.artistId(), ingest.trackIngestId()));
    }
}
