package org.sona.e2e;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.db.ArtistDao;
import org.sona.db.PasswordDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.UserDao;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@E2ETest
class AlbumsE2ETest {

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

    @BeforeEach
    void setUp() {
        new TestUsers(userDao, passwordDao, encoder).createUser("alice", UserRole.USER);
    }

    @Test
    void albumsAreListedByNameWithTheirArtistsAndAnyTypeButAlbum(final Page page) {
        final var coldplay = insertArtist("Coldplay");
        final var keane = insertArtist("Keane");
        insertReleaseGroup(coldplay, "Parachutes", ReleaseGroupType.ALBUM);
        insertReleaseGroup(keane, "Hopes and Fears", ReleaseGroupType.ALBUM);
        insertReleaseGroup(keane, "Somewhere Only We Know", ReleaseGroupType.SINGLE);

        final var albums = logInAndOpen(page);

        assertThat(albums.titles()).hasText(new String[]{"Hopes and Fears", "Parachutes", "Somewhere Only We Know"});
        assertThat(albums.subtitle("Hopes and Fears")).hasText("Keane");
        assertThat(albums.subtitle("Somewhere Only We Know")).hasText("Keane · Single");
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
        return artistDao.upsert(new Artist(uuidv7(), name, "GB", null, uuidv7()));
    }

    private ReleaseGroup insertReleaseGroup(final Artist artist, final String name, final ReleaseGroupType type) {
        return releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), name, type, null, artist.artistId(), uuidv7()));
    }
}
