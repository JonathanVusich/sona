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
import org.sona.db.IngestDao;
import org.sona.db.PlayDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
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
class DiscoverE2ETest {

    // The track is ten seconds of silence, and the browser takes a moment to start it.
    private static final LocatorAssertions.HasTextOptions TRACK_LENGTH =
            new LocatorAssertions.HasTextOptions().setTimeout(15_000);

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
    PlayDao playDao;

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
    void recentlyPlayedListsTheTracksPlayedLatestFirst(final Page page) {
        final var yellow = insertTrack("Yellow", 1);
        final var shiver = insertTrack("Shiver", 2);
        playDao.insert(alice.userId(), yellow.trackId());
        playDao.insert(alice.userId(), shiver.trackId());
        playDao.insert(alice.userId(), yellow.trackId());
        logIn(page);

        final var discoverPage = new DiscoverPage(page);

        assertThat(discoverPage.playedTrackTitles()).hasText(new String[] {"Yellow", "Shiver", "Yellow"});
        assertThat(discoverPage.playedTrack("Shiver")).containsText("Coldplay");
        assertThat(discoverPage.playedTrack("Shiver")).containsText("Parachutes");
    }

    @Test
    void recentlyPlayedIsEmptyBeforeTheUserPlaysAnything(final Page page) {
        insertTrack("Yellow", 1);
        logIn(page);

        assertThat(new DiscoverPage(page).playedMessage()).isVisible();
    }

    @Test
    void anotherUsersPlaysAreNotListed(final Page page) {
        final var bob = new TestUsers(userDao, encoder).createUser("bob", UserRole.USER);
        playDao.insert(bob.userId(), insertTrack("Yellow", 1).trackId());
        logIn(page);

        assertThat(new DiscoverPage(page).playedMessage()).isVisible();
    }

    @Test
    void clickingAPlayedTrackPlaysIt(final Page page) throws IOException {
        final var yellow = insertTrackWithFile("Yellow", 1);
        playDao.insert(alice.userId(), yellow.trackId());
        logIn(page);
        final var discoverPage = new DiscoverPage(page);
        final var player = new PlayerBar(page);

        discoverPage.trackPlayButton("Yellow").click();

        assertThat(player.trackTitle()).hasText("Yellow");
        assertThat(player.time()).hasText(Pattern.compile("^0:0[1-9] "));
    }

    @Test
    void aTrackPlayedForHalfItsLengthIsListed(final Page page) throws IOException {
        insertTrackWithFile("Yellow", 1);
        logIn(page);
        final var discoverPage = new DiscoverPage(page);
        final var releasePage = new ReleasePage(page);
        releasePage.open(parachutes.releaseId());
        final var player = new PlayerBar(page);

        releasePage.actionButton("Play").click();
        // The track is ten seconds long, so five seconds have played by then.
        assertThat(player.time()).hasText(Pattern.compile("^0:0[6-9] "), TRACK_LENGTH);
        discoverPage.openFromMenu();

        assertThat(discoverPage.playedTrackTitles()).hasText(new String[] {"Yellow"});
    }

    @Test
    void aTrackStoppedEarlyIsNotListed(final Page page) throws IOException {
        insertTrackWithFile("Yellow", 1);
        logIn(page);
        final var discoverPage = new DiscoverPage(page);
        final var releasePage = new ReleasePage(page);
        releasePage.open(parachutes.releaseId());
        final var player = new PlayerBar(page);

        releasePage.actionButton("Play").click();
        assertThat(player.time()).hasText(Pattern.compile("^0:0[1-2] "));
        player.button("Pause").click();
        discoverPage.openFromMenu();

        assertThat(discoverPage.playedMessage()).isVisible();
    }

    private static void logIn(final Page page) {
        new LoginPage(page).logIn("alice", "alice");
        assertThat(page).hasURL(Pattern.compile("/discover$"));
    }

    private Track insertTrackWithFile(final String name, final int position) throws IOException {
        final var track = insertTrack(name, position);
        final var file = libraryProperties.mediaFolder().resolve(track.path());
        Files.createDirectories(file.getParent());
        try (final var audio = FlacAudioSample.SILENCE_10_SECONDS.inputStream()) {
            Files.copy(audio, file);
        }
        return track;
    }

    // Stored and imported, without a file, for tests that don't stream it.
    private Track insertTrack(final String name, final int position) {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId());
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
