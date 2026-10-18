package org.sona.e2e;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.db.ArtistDao;
import org.sona.db.LabelDao;
import org.sona.db.MediumDao;
import org.sona.db.PasswordDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.ReleaseLabelDao;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestState;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@E2ETest
class ReleaseE2ETest {

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
    LabelDao labelDao;
    @Autowired
    ReleaseLabelDao releaseLabelDao;
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
    void aReleaseListsItsTracksDiscByDiscWithEachTracksArtist(final Page page) {
        final var various = artistDao.upsert(new Artist(uuidv7(), "Various Artists", null, null, 0, uuidv7()));
        final var coldplay = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var keane = artistDao.upsert(new Artist(uuidv7(), "Keane", "GB", null, 0, uuidv7()));
        final var release = insertRelease(various, "Britpop", "Parlophone");
        final var disc1 = mediumDao.upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), 1, "CD"))).getFirst();
        final var disc2 = mediumDao.upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), 2, "CD"))).getFirst();
        insertTrack(disc1, coldplay, "Yellow");
        insertTrack(disc2, keane, "Somewhere Only We Know");
        final var releasePage = logInAndOpen(page, release);

        assertThat(releasePage.title()).hasText("Britpop");
        assertThat(releasePage.link("Various Artists")).isVisible();
        assertThat(releasePage.details()).hasText("2002 · 2×CD · Parlophone · GB · 2 songs · 10 min");
        assertThat(releasePage.link("All editions"))
                .hasAttribute("href", "/release-groups/" + release.releaseGroupId());
        assertThat(releasePage.disc("Disc 1 · CD")).isVisible();
        assertThat(releasePage.disc("Disc 2 · CD")).isVisible();
        assertThat(releasePage.trackArtist("Yellow", "Coldplay")).isVisible();
        assertThat(releasePage.trackArtist("Somewhere Only We Know", "Keane")).isVisible();
    }

    @Test
    void aSingleDiscReleaseNamesNeitherTheDiscNorTheReleasesOwnArtistOnEachTrack(final Page page) {
        final var coldplay = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var release = insertRelease(coldplay, "Parachutes", "Parlophone");
        final var disc = mediumDao.upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), 1, "CD"))).getFirst();
        insertTrack(disc, coldplay, "Yellow");
        final var releasePage = logInAndOpen(page, release);

        assertThat(releasePage.track("Yellow")).isVisible();
        assertThat(releasePage.disc("Disc 1 · CD")).isHidden();
        assertThat(releasePage.track("Yellow")).not().containsText("Coldplay");
    }

    @Test
    void aDiscWithNoTrackInTheLibraryIsLeftOut(final Page page) {
        final var coldplay = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var release = insertRelease(coldplay, "Live 2003", "Parlophone");
        insertTrack(insertMedium(release, 1, "CD"), coldplay, "Clocks");
        insertMedium(release, 2, "DVD");
        final var releasePage = logInAndOpen(page, release);

        assertThat(releasePage.track("Clocks")).isVisible();
        // The DVD still describes the release, but only one disc is shown, so it needs no heading.
        assertThat(releasePage.details()).containsText("CD + DVD");
        assertThat(releasePage.disc("Disc 1 · CD")).isHidden();
        assertThat(releasePage.disc("Disc 2 · DVD")).isHidden();
    }

    @Test
    void aReleaseListsItsGroupsOtherEditionsAndTheArtistsOtherReleaseGroups(final Page page) {
        final var coldplay = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var original = insertRelease(coldplay, "A Rush of Blood to the Head", "Parlophone");
        insertTrack(insertMedium(original, 1, "CD"), coldplay, "Clocks");
        final var japan = releaseDao.upsert(new Release(uuidv7(), "A Rush of Blood to the Head (Japan)", null,
                LocalDate.of(2002, 8, 27), "JP", 0, original.releaseGroupId(), coldplay.artistId(), uuidv7()));
        insertTrack(insertMedium(japan, 1, "CD"), coldplay, "The Scientist");
        // Not in the library, as it has no track.
        releaseDao.upsert(new Release(uuidv7(), "A Rush of Blood to the Head (Deluxe)", null,
                LocalDate.of(2002, 8, 28), "GB", 0, original.releaseGroupId(), coldplay.artistId(), uuidv7()));
        insertTrack(insertMedium(insertRelease(coldplay, "Parachutes", "Parlophone"), 1, "CD"), coldplay, "Yellow");
        final var releasePage = logInAndOpen(page, original);

        assertThat(releasePage.editionTitles()).hasText(new String[]{"A Rush of Blood to the Head (Japan)"});
        assertThat(releasePage.moreByTitles()).hasText(new String[]{"Parachutes"});
    }

    @Test
    void aTrackWhoseFileIsntInTheLibraryIsLeftOut(final Page page) {
        final var coldplay = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var release = insertRelease(coldplay, "Parachutes", "Parlophone");
        final var disc = insertMedium(release, 1, "CD");
        insertTrack(disc, coldplay, "Yellow");
        insertTrackInState(disc, coldplay, "Shiver", IngestState.MOVE_FAILED);
        final var releasePage = logInAndOpen(page, release);

        assertThat(releasePage.track("Yellow")).isVisible();
        assertThat(releasePage.track("Shiver")).hasCount(0);
        assertThat(releasePage.details()).containsText("1 song");
    }

    @Test
    void aReleaseWithNoTrackInTheLibraryIsReported(final Page page) {
        final var coldplay = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var release = insertRelease(coldplay, "Parachutes", "Parlophone");
        insertTrackInState(insertMedium(release, 1, "CD"), coldplay, "Yellow", IngestState.MOVE_FAILED);
        final var releasePage = logInAndOpen(page, release);

        assertThat(releasePage.message()).hasText("This release isn't in the library.");
    }

    @Test
    void aReleaseThatIsntInTheLibraryIsReported(final Page page) {
        new LoginPage(page).logIn("alice", "alice");
        assertThat(page).hasURL(Pattern.compile("/discover$"));
        final var releasePage = new ReleasePage(page);

        releasePage.open(uuidv7());

        assertThat(releasePage.message()).hasText("This release isn't in the library.");
    }

    private ReleasePage logInAndOpen(final Page page, final Release release) {
        new LoginPage(page).logIn("alice", "alice");
        // Opening the release before the login finishes would reload the page without a session.
        assertThat(page).hasURL(Pattern.compile("/discover$"));
        final var releasePage = new ReleasePage(page);
        releasePage.open(release.releaseId());
        return releasePage;
    }

    private Release insertRelease(final Artist artist, final String name, final String labelName) {
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), name, null, null,
                artist.artistId(), 0, uuidv7()));
        final var release = releaseDao.upsert(new Release(uuidv7(), name, null, LocalDate.of(2002, 8, 26), "GB", 0,
                releaseGroup.releaseGroupId(), artist.artistId(), uuidv7()));
        final var label = labelDao.upsertAll(List.of(new Label(uuidv7(), labelName, uuidv7()))).getFirst();
        releaseLabelDao.upsertAll(List.of(new ReleaseLabel(release.releaseId(), label.labelId())));
        return release;
    }

    private Medium insertMedium(final Release release, final int position, final String format) {
        return mediumDao.upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), position, format))).getFirst();
    }

    private void insertTrack(final Medium medium, final Artist artist, final String name) {
        insertTrackInState(medium, artist, name, IngestState.COMPLETED);
    }

    private void insertTrackInState(final Medium medium, final Artist artist, final String name,
                                    final IngestState state) {
        final var ingest = new TrackToIngest(uuidv7(), uuidv7(), name + ".flac", state);
        trackToIngestDao.insert(ingest);
        trackDao.insert(new Track(uuidv7(), name, 300, AudioFormat.FLAC, name + ".flac", medium.mediumId(), 1,
                artist.artistId(), ingest.trackIngestId()));
    }
}
