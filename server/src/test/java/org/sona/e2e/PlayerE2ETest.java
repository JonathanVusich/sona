package org.sona.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.config.properties.LibraryProperties;
import org.sona.db.ArtistDao;
import org.sona.db.IngestGroupDao;
import org.sona.db.MediumDao;
import org.sona.db.PasswordDao;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@E2ETest
class PlayerE2ETest {

    // The tracks are ten seconds of silence, so one plays to its end within the wait.
    private static final LocatorAssertions.HasTextOptions TRACK_LENGTH =
            new LocatorAssertions.HasTextOptions().setTimeout(15_000);

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordDao passwordDao;

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
    MediumDao mediumDao;

    @Autowired
    TrackDao trackDao;

    @Autowired
    IngestGroupDao ingestGroupDao;

    @Autowired
    TrackToIngestDao trackToIngestDao;

    Artist coldplay;
    Release parachutes;
    Medium disc;

    @BeforeEach
    void setUp() {
        new TestUsers(userDao, passwordDao, encoder).createUser("alice", UserRole.USER);
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
        disc = mediumDao
                .upsertAll(List.of(new Medium(uuidv7(), parachutes.releaseId(), 1, "CD")))
                .getFirst();
    }

    @Test
    void playingAReleaseStreamsItsTracksOneAfterAnother(final Page page) throws IOException {
        insertTrack("Don't Panic", 1);
        insertTrack("Shiver", 2);
        final var releasePage = logInAndOpenRelease(page);
        final var player = new PlayerBar(page);

        releasePage.actionButton("Play").click();

        assertThat(player.trackTitle()).hasText("Don't Panic");
        assertThat(player.artist()).hasText("Coldplay");
        assertThat(player.button("Pause")).isVisible();
        // The position moves only as the browser plays the stream.
        assertThat(player.time()).hasText(Pattern.compile("^0:0[1-9] / 0:10$"));
        assertThat(player.trackTitle()).hasText("Shiver", TRACK_LENGTH);
    }

    @Test
    void pausingStopsTheTrackWhereItIs(final Page page) throws IOException {
        insertTrack("Yellow", 1);
        final var releasePage = logInAndOpenRelease(page);
        final var player = new PlayerBar(page);
        releasePage.actionButton("Play").click();
        assertThat(player.time()).hasText(Pattern.compile("^0:0[1-9] "));

        player.button("Pause").click();
        final var pausedAt = player.time().textContent();
        page.waitForTimeout(2_000);

        assertThat(player.button("Play")).isVisible();
        assertThat(player.time()).hasText(pausedAt);
    }

    @Test
    void clickingATrackOnTheReleaseGroupPagePlaysIt(final Page page) throws IOException {
        insertTrack("Don't Panic", 1);
        insertTrack("Shiver", 2);
        logIn(page);
        final var releaseGroupPage = new ReleaseGroupPage(page);
        releaseGroupPage.open(parachutes.releaseGroupId());
        final var player = new PlayerBar(page);

        releaseGroupPage.trackPlayButton("Shiver").click();

        assertThat(player.trackTitle()).hasText("Shiver");
        assertThat(player.time()).hasText(Pattern.compile("^0:0[1-9] "));
    }

    @Test
    void anAlbumCardsPlayButtonPlaysTheAlbum(final Page page) throws IOException {
        insertTrack("Don't Panic", 1);
        insertTrack("Shiver", 2);
        logIn(page);
        final var albumsPage = new AlbumsPage(page);
        albumsPage.open();
        final var player = new PlayerBar(page);

        albumsPage.album("Parachutes").hover();
        albumsPage.playButton("Parachutes").click();

        assertThat(player.trackTitle()).hasText("Don't Panic");
        assertThat(player.time()).hasText(Pattern.compile("^0:0[1-9] "));
    }

    @Test
    void aTrackWhoseFileCantBeStreamedIsReported(final Page page) {
        insertTrackWithoutFile("Yellow");
        final var releasePage = logInAndOpenRelease(page);
        final var player = new PlayerBar(page);

        releasePage.actionButton("Play").click();

        assertThat(player.toast()).hasText("Couldn't play Yellow.");
        assertThat(player.button("Play")).isVisible();
    }

    private ReleasePage logInAndOpenRelease(final Page page) {
        logIn(page);
        final var releasePage = new ReleasePage(page);
        releasePage.open(parachutes.releaseId());
        return releasePage;
    }

    private static void logIn(final Page page) {
        new LoginPage(page).logIn("alice", "alice");
        // Opening a page before the login finishes would reload it without a session.
        assertThat(page).hasURL(Pattern.compile("/discover$"));
    }

    private void insertTrack(final String name, final int position) throws IOException {
        final var track = insertTrackRow(name, position);
        final var file = libraryProperties.mediaFolder().resolve(track.path());
        Files.createDirectories(file.getParent());
        try (final var audio = FlacAudioSample.SILENCE_10_SECONDS.inputStream()) {
            Files.copy(audio, file);
        }
    }

    // Stored and imported, but its file has gone from the library.
    private void insertTrackWithoutFile(final String name) {
        insertTrackRow(name, 1);
    }

    private Track insertTrackRow(final String name, final int position) {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId());
        ingestGroupDao.insert(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), name + ".flac", IngestState.COMPLETED);
        trackToIngestDao.insert(ingest);
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
