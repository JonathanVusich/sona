package org.sona.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import com.microsoft.playwright.Page;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.db.ArtistDao;
import org.sona.db.IngestDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.ReleaseGroupType;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@E2ETest
class AlbumsE2ETest {

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    ArtistDao artistDao;

    @Autowired
    ReleaseGroupDao releaseGroupDao;

    @Autowired
    ReleaseDao releaseDao;

    @Autowired
    TrackDao trackDao;

    @Autowired
    IngestDao ingestDao;

    @BeforeEach
    void setUp() {
        new TestUsers(userDao, encoder).createUser("alice", UserRole.USER);
    }

    @Test
    void albumsAreListedByNameWithTheirArtistsAndAnyTypeButAlbum(final Page page) {
        final var coldplay = insertArtist("Coldplay");
        final var keane = insertArtist("Keane");
        insertReleaseGroup(coldplay, "Parachutes", ReleaseGroupType.ALBUM);
        insertReleaseGroup(keane, "Hopes and Fears", ReleaseGroupType.ALBUM);
        insertReleaseGroup(keane, "Somewhere Only We Know", ReleaseGroupType.SINGLE);

        final var albums = logInAndOpen(page);

        assertThat(albums.titles()).hasText(new String[] {"Hopes and Fears", "Parachutes", "Somewhere Only We Know"});
        assertThat(albums.subtitle("Hopes and Fears")).hasText("Keane");
        assertThat(albums.subtitle("Somewhere Only We Know")).hasText("Keane · Single");
    }

    @Test
    void albumsNotInTheLibraryArentListed(final Page page) {
        final var coldplay = insertArtist("Coldplay");
        insertReleaseGroup(coldplay, "Parachutes", ReleaseGroupType.ALBUM);
        // An ingest that stopped part-way, after storing the album, its release, or its track's row.
        releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), "X&Y", ReleaseGroupType.ALBUM, null, coldplay.artistId(), 0, uuidv7()));
        insertRelease(coldplay, "Viva la Vida", ReleaseGroupType.ALBUM);
        insertTrackInState(insertRelease(coldplay, "Mylo Xyloto", ReleaseGroupType.ALBUM), IngestState.MOVE_FAILED);

        final var albums = logInAndOpen(page);

        assertThat(albums.titles()).hasText(new String[] {"Parachutes"});
    }

    @Test
    void anAlbumOpensItsReleaseGroupsPage(final Page page) {
        final var parachutes = insertReleaseGroup(insertArtist("Coldplay"), "Parachutes", ReleaseGroupType.ALBUM);
        final var albums = logInAndOpen(page);

        albums.album("Parachutes").click();

        assertThat(page).hasURL(Pattern.compile("/release-groups/" + parachutes.releaseGroupId() + "$"));
        assertThat(new ReleaseGroupPage(page).title()).hasText("Parachutes");
    }

    @Test
    void moreAlbumsLoadAsTheUserScrolls(final Page page) {
        final var artist = insertArtist("Prolific");
        // One more than a page holds.
        for (var number = 1; number <= 51; number++) {
            insertReleaseGroup(artist, "Album %02d".formatted(number), ReleaseGroupType.ALBUM);
        }
        final var albums = logInAndOpen(page);
        assertThat(albums.titles()).hasCount(50);

        albums.scrollToEnd();

        assertThat(albums.titles()).hasCount(51);
        assertThat(albums.album("Album 51")).isVisible();
    }

    @Test
    void anEmptyLibrarySaysSo(final Page page) {
        final var albums = logInAndOpen(page);

        assertThat(albums.message()).hasText("No albums in the library yet.");
    }

    private AlbumsPage logInAndOpen(final Page page) {
        new LoginPage(page).logIn("alice", "alice");
        // Opening the albums before the login finishes would reload the page without a session.
        assertThat(page).hasURL(Pattern.compile("/discover$"));
        final var albums = new AlbumsPage(page);
        albums.open();
        return albums;
    }

    private Artist insertArtist(final String name) {
        return artistDao.upsert(new Artist(uuidv7(), name, "GB", null, 0, uuidv7()));
    }

    /**
     * @return a release group in the library, with a release that has a track
     */
    private ReleaseGroup insertReleaseGroup(final Artist artist, final String name, final ReleaseGroupType type) {
        final var release = insertRelease(artist, name, type);
        insertTrack(release);
        return releaseGroupDao.loadById(release.releaseGroupId()).orElseThrow();
    }

    /**
     * @return the only release of a new release group, with no track, so neither is in the library
     */
    private Release insertRelease(final Artist artist, final String name, final ReleaseGroupType type) {
        final var group =
                releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), name, type, null, artist.artistId(), 0, uuidv7()));
        return releaseDao.upsert(
                new Release(uuidv7(), name, null, null, "GB", 0, group.releaseGroupId(), artist.artistId(), uuidv7()));
    }

    /**
     * Gives the release a track, so it's in the library.
     */
    private void insertTrack(final Release release) {
        insertTrackInState(release, IngestState.COMPLETED);
    }

    private void insertTrackInState(final Release release, final IngestState state) {
        final var medium = releaseDao
                .upsertMedia(List.of(new Medium(uuidv7(), release.releaseId(), 1, "CD")))
                .getFirst();
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId(), 1);
        ingestDao.insertGroup(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "track.flac", state);
        ingestDao.insertTrackIngest(ingest);
        trackDao.insert(new Track(
                uuidv7(),
                release.name(),
                300,
                AudioFormat.FLAC,
                "track.flac",
                medium.mediumId(),
                1,
                release.artistId(),
                ingest.trackIngestId()));
    }
}
