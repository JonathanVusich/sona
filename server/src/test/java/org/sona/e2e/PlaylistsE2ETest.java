package org.sona.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import com.microsoft.playwright.Page;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.config.properties.LibraryProperties;
import org.sona.db.ArtistDao;
import org.sona.db.IngestDao;
import org.sona.db.PlaylistDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestGroupState;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Playlist;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.pojos.Users;
import org.sona.samples.FlacAudioSample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@E2ETest
class PlaylistsE2ETest {

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    LibraryProperties libraryProperties;

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

    @Autowired
    PlaylistDao playlistDao;

    Users alice;
    Artist coldplay;
    Release parachutes;
    Medium disc;

    @BeforeEach
    void setUp() {
        alice = new TestUsers(userDao, encoder).createUser("alice", UserRole.USER);
        coldplay = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var releaseGroup = releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), "Parachutes", null, null, coldplay.artistId(), 0, uuidv7()));
        parachutes = releaseDao.upsert(new Release(
                uuidv7(),
                "Parachutes",
                null,
                LocalDate.of(2000, 7, 10),
                "GB",
                0,
                releaseGroup.releaseGroupId(),
                coldplay.artistId(),
                uuidv7()));
        disc = releaseDao
                .upsertMedia(List.of(new Medium(uuidv7(), parachutes.releaseId(), 1, "CD")))
                .getFirst();
    }

    @Test
    void creatingAPlaylistOpensItsEmptyPage(final Page page) {
        logIn(page);
        final var playlistsPage = new PlaylistsPage(page);
        playlistsPage.open();
        assertThat(playlistsPage.message()).hasText("You haven't made any playlists yet.");

        playlistsPage.newPlaylistButton().click();
        final var dialog = new PlaylistDialog(page);
        dialog.fill("Late Night", "Slow and quiet.");
        dialog.submit("Create");

        final var playlistPage = new PlaylistPage(page);
        assertThat(page).hasURL(Pattern.compile("/playlists/[0-9a-f-]{36}$"));
        assertThat(playlistPage.title()).hasText("Late Night");
        assertThat(playlistPage.description()).hasText("Slow and quiet.");
        assertThat(playlistPage.message()).hasText("Add songs from any album with the ⋯ button on a track.");
        assertThat(playlistPage.actionButton("Play")).isDisabled();
        playlistsPage.open();
        assertThat(playlistsPage.playlist("Late Night")).containsText("0 songs");
    }

    @Test
    void aTrackIsAddedToAPlaylistFromItsReleasesTrackMenu(final Page page) {
        insertTrackRow("Yellow", 1);
        final var lateNight = insertPlaylist("Late Night");
        logIn(page);
        final var releasePage = new ReleasePage(page);
        releasePage.open(parachutes.releaseId());
        final var toast = new PlayerBar(page).toast();

        releasePage.openTrackMenu("Yellow");
        releasePage.menuItem("Late Night").click();
        assertThat(toast).hasText("Added to Late Night");
        releasePage.openTrackMenu("Yellow");
        releasePage.menuItem("Late Night").click();
        assertThat(toast).hasText("Already on Late Night");

        final var playlistPage = new PlaylistPage(page);
        playlistPage.open(lateNight.playlistId());
        assertThat(playlistPage.trackTitles()).hasText(new String[] {"Yellow"});
        assertThat(playlistPage.track("Yellow")).containsText("Parachutes");
    }

    @Test
    void aTrackStartsANewPlaylistFromItsReleasesTrackMenu(final Page page) {
        insertTrackRow("Yellow", 1);
        logIn(page);
        final var releasePage = new ReleasePage(page);
        releasePage.open(parachutes.releaseId());

        releasePage.openTrackMenu("Yellow");
        releasePage.menuItem("New playlist…").click();
        final var dialog = new PlaylistDialog(page);
        dialog.fill("Road Trip", "");
        dialog.submit("Create");

        assertThat(new PlayerBar(page).toast()).hasText("Added to Road Trip");
        final var playlistsPage = new PlaylistsPage(page);
        playlistsPage.open();
        assertThat(playlistsPage.playlist("Road Trip")).containsText("1 song");
    }

    @Test
    void removingATrackTakesItOffThePlaylist(final Page page) {
        final var lateNight = insertPlaylist("Late Night", insertTrackRow("Yellow", 1), insertTrackRow("Shiver", 2));
        logIn(page);
        final var playlistPage = new PlaylistPage(page);
        playlistPage.open(lateNight.playlistId());

        playlistPage.openTrackMenu("Yellow");
        playlistPage.menuItem("Remove from this playlist").click();

        assertThat(playlistPage.trackTitles()).hasText(new String[] {"Shiver"});
        page.reload();
        assertThat(playlistPage.trackTitles()).hasText(new String[] {"Shiver"});
    }

    @Test
    void draggingATrackOntoAnotherMovesItToThatPlace(final Page page) {
        final var lateNight = insertPlaylist(
                "Late Night",
                insertTrackRow("Don't Panic", 1),
                insertTrackRow("Shiver", 2),
                insertTrackRow("Yellow", 3));
        logIn(page);
        final var playlistPage = new PlaylistPage(page);
        playlistPage.open(lateNight.playlistId());

        playlistPage.dragTrack("Yellow", "Don't Panic");

        assertThat(playlistPage.trackTitles()).hasText(new String[] {"Yellow", "Don't Panic", "Shiver"});
        page.reload();
        assertThat(playlistPage.trackTitles()).hasText(new String[] {"Yellow", "Don't Panic", "Shiver"});
    }

    @Test
    void editingAPlaylistsDetailsRenamesIt(final Page page) {
        final var lateNight = insertPlaylist("Late Night");
        logIn(page);
        final var playlistPage = new PlaylistPage(page);
        playlistPage.open(lateNight.playlistId());

        playlistPage.actionButton("More").click();
        playlistPage.menuItem("Edit details").click();
        final var dialog = new PlaylistDialog(page);
        dialog.fill("After Midnight", "Quieter still.");
        dialog.submit("Save");

        assertThat(playlistPage.title()).hasText("After Midnight");
        page.reload();
        assertThat(playlistPage.title()).hasText("After Midnight");
        assertThat(playlistPage.description()).hasText("Quieter still.");
    }

    @Test
    void deletingAPlaylistReturnsToTheListWithoutIt(final Page page) {
        final var lateNight = insertPlaylist("Late Night", insertTrackRow("Yellow", 1));
        insertPlaylist("Focus");
        logIn(page);
        final var playlistPage = new PlaylistPage(page);
        playlistPage.open(lateNight.playlistId());

        playlistPage.actionButton("More").click();
        playlistPage.menuItem("Delete playlist").click();
        playlistPage.dialogButton("Delete").click();

        final var playlistsPage = new PlaylistsPage(page);
        assertThat(page).hasURL(Pattern.compile("/playlists$"));
        assertThat(playlistsPage.playlistTitles()).hasText(new String[] {"Focus"});
    }

    @Test
    void playingAPlaylistStreamsItsTracksInItsOrder(final Page page) throws IOException {
        final var lateNight = insertPlaylist("Late Night", insertTrack("Shiver", 2), insertTrack("Yellow", 1));
        logIn(page);
        final var playlistPage = new PlaylistPage(page);
        playlistPage.open(lateNight.playlistId());
        final var player = new PlayerBar(page);

        playlistPage.actionButton("Play").click();

        assertThat(player.trackTitle()).hasText("Shiver");
        assertThat(player.time()).hasText(Pattern.compile("^0:0[1-9] "));
    }

    @Test
    void anotherUsersPlaylistDoesntExistForThem(final Page page) {
        final var bob = new TestUsers(userDao, encoder).createUser("bob", UserRole.USER);
        final var bobs = new Playlist(uuidv7(), bob.userId(), "Bob's", null, 0);
        playlistDao.insert(bobs);
        logIn(page);
        final var playlistPage = new PlaylistPage(page);

        playlistPage.open(bobs.playlistId());
        assertThat(playlistPage.message()).hasText("This playlist doesn't exist.");
        playlistPage.open(UUID.randomUUID());
        assertThat(playlistPage.message()).hasText("This playlist doesn't exist.");
    }

    private static void logIn(final Page page) {
        new LoginPage(page).logIn("alice", "alice");
        // Opening a page before the login finishes would reload it without a session.
        assertThat(page).hasURL(Pattern.compile("/discover$"));
    }

    private Playlist insertPlaylist(final String name, final Track... tracks) {
        final var playlist = new Playlist(uuidv7(), alice.userId(), name, null, 0);
        playlistDao.insert(playlist);
        if (tracks.length > 0) {
            final var trackIds = Arrays.stream(tracks).map(Track::trackId).toList();
            playlistDao.insertTracks(alice.userId(), playlist.playlistId(), trackIds);
        }
        return playlist;
    }

    private Track insertTrack(final String name, final int position) throws IOException {
        final var track = insertTrackRow(name, position);
        final var file = libraryProperties.mediaFolder().resolve(track.path());
        Files.createDirectories(file.getParent());
        try (final var audio = FlacAudioSample.SILENCE_10_SECONDS.inputStream()) {
            Files.copy(audio, file);
        }
        return track;
    }

    // Stored and imported, without a file, for tests that don't play it.
    private Track insertTrackRow(final String name, final int position) {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(
                uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId(), IngestGroupState.MATCHED);
        ingestDao.insertGroup(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), name + ".flac", IngestState.COMPLETED);
        ingestDao.insertTrackIngest(ingest);
        final var trackId = uuidv7();
        final var track = new Track(
                trackId,
                name,
                10,
                AudioFormat.FLAC,
                "%s/%s.flac".formatted(parachutes.releaseId(), trackId),
                disc.mediumId(),
                position,
                coldplay.artistId(),
                ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }
}
