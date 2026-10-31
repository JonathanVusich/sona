package org.sona.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import com.microsoft.playwright.Page;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
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
import org.sona.model.enums.IngestGroupState;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@E2ETest
class ReleaseGroupE2ETest {

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
    void aReleaseGroupListsItsEditionsEarliestFirst(final Page page) {
        final var keane = artistDao.upsert(new Artist(uuidv7(), "Keane", "GB", null, 0, uuidv7()));
        final var group = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "Hopes and Fears",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10),
                keane.artistId(),
                0,
                uuidv7()));
        final var island = insertLabel("Island");
        final var deluxe = insertRelease(group, "Hopes and Fears (Deluxe Edition)", LocalDate.of(2014, 5, 12));
        insertTrack(insertMedia(deluxe, "CD", "CD").getFirst(), keane, "Snowed Under");
        linkLabels(deluxe, island);
        final var original = insertRelease(group, "Hopes and Fears", LocalDate.of(2004, 5, 10));
        final var disc = insertMedia(original, "CD").getFirst();
        linkLabels(original, island, insertLabel("Interscope"));
        insertTrack(disc, keane, "Somewhere Only We Know");
        final var releaseGroupPage = logInAndOpen(page, group);

        assertThat(releaseGroupPage.title()).hasText("Hopes and Fears");
        assertThat(releaseGroupPage.type()).hasText("Album");
        assertThat(releaseGroupPage.link("Keane")).isVisible();
        assertThat(releaseGroupPage.details()).hasText("2004 · 2 editions");
        assertThat(releaseGroupPage.editionTitles())
                .hasText(new String[] {"Hopes and Fears", "Hopes and Fears (Deluxe Edition)"});
        assertThat(releaseGroupPage.editionDetails()).hasText(new String[] {
            "2004 · CD · Interscope, Island · GB · 1 song", "2014 · 2×CD · Island · GB · 1 song"
        });
    }

    @Test
    void aReleaseGroupListsItsEarliestEditionsTracksDiscByDisc(final Page page) {
        final var keane = artistDao.upsert(new Artist(uuidv7(), "Keane", "GB", null, 0, uuidv7()));
        final var group = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "Hopes and Fears",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10),
                keane.artistId(),
                0,
                uuidv7()));
        final var deluxe = insertRelease(group, "Hopes and Fears (Deluxe Edition)", LocalDate.of(2014, 5, 12));
        insertTrack(insertMedia(deluxe, "CD").getFirst(), keane, "Snowed Under");
        final var original = insertRelease(group, "Hopes and Fears", LocalDate.of(2004, 5, 10));
        final var discs = insertMedia(original, "CD", "DVD");
        insertTrack(discs.getFirst(), keane, "Somewhere Only We Know");
        insertTrack(discs.getLast(), keane, "Everybody's Changing");
        final var releaseGroupPage = logInAndOpen(page, group);

        assertThat(releaseGroupPage.disc("Disc 1 · CD")).isVisible();
        assertThat(releaseGroupPage.disc("Disc 2 · DVD")).isVisible();
        assertThat(releaseGroupPage.track("Somewhere Only We Know")).isVisible();
        assertThat(releaseGroupPage.track("Everybody's Changing")).isVisible();
        assertThat(releaseGroupPage.track("Snowed Under")).hasCount(0);
    }

    @Test
    void aReleaseGroupListsTheArtistsOtherReleaseGroupsNewestFirst(final Page page) {
        final var keane = artistDao.upsert(new Artist(uuidv7(), "Keane", "GB", null, 0, uuidv7()));
        final var group = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "Hopes and Fears",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10),
                keane.artistId(),
                0,
                uuidv7()));
        insertReleaseWithTrack(group, keane, "Hopes and Fears", LocalDate.of(2004, 5, 10));
        final var underTheIronSea = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "Under the Iron Sea",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2006, 6, 12),
                keane.artistId(),
                0,
                uuidv7()));
        insertReleaseWithTrack(underTheIronSea, keane, "Under the Iron Sea", LocalDate.of(2006, 6, 12));
        final var perfectSymmetry = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "Perfect Symmetry",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2008, 10, 13),
                keane.artistId(),
                0,
                uuidv7()));
        insertReleaseWithTrack(perfectSymmetry, keane, "Perfect Symmetry", LocalDate.of(2008, 10, 13));
        // Not in the library until a release of it with a track is stored.
        releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "Strangeland",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2012, 5, 7),
                keane.artistId(),
                0,
                uuidv7()));
        final var releaseGroupPage = logInAndOpen(page, group);

        assertThat(releaseGroupPage.moreByTitles()).hasText(new String[] {"Perfect Symmetry", "Under the Iron Sea"});
    }

    @Test
    void anEarlierEditionWithNoTrackInTheLibraryDoesntStandForTheReleaseGroup(final Page page) {
        final var keane = artistDao.upsert(new Artist(uuidv7(), "Keane", "GB", null, 0, uuidv7()));
        final var group = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "Hopes and Fears",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10),
                keane.artistId(),
                0,
                uuidv7()));
        insertMedia(insertRelease(group, "Hopes and Fears", LocalDate.of(2004, 5, 10)), "CD");
        insertReleaseWithTrack(group, keane, "Hopes and Fears (Deluxe Edition)", LocalDate.of(2014, 5, 12));
        final var releaseGroupPage = logInAndOpen(page, group);

        assertThat(releaseGroupPage.track("Hopes and Fears (Deluxe Edition)")).isVisible();
        assertThat(releaseGroupPage.editionTitles()).hasText(new String[] {"Hopes and Fears (Deluxe Edition)"});
    }

    @Test
    void aReleaseGroupWithNoTrackInTheLibraryIsReported(final Page page) {
        final var keane = artistDao.upsert(new Artist(uuidv7(), "Keane", "GB", null, 0, uuidv7()));
        final var group = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "Hopes and Fears",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10),
                keane.artistId(),
                0,
                uuidv7()));
        insertMedia(insertRelease(group, "Hopes and Fears", LocalDate.of(2004, 5, 10)), "CD");
        final var releaseGroupPage = logInAndOpen(page, group);

        assertThat(releaseGroupPage.message()).hasText("This release group isn't in the library.");
    }

    @Test
    void anEditionOpensItsReleasesPage(final Page page) {
        final var keane = artistDao.upsert(new Artist(uuidv7(), "Keane", "GB", null, 0, uuidv7()));
        final var group = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "Hopes and Fears",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10),
                keane.artistId(),
                0,
                uuidv7()));
        final var original = insertReleaseWithTrack(group, keane, "Hopes and Fears", LocalDate.of(2004, 5, 10));
        final var releaseGroupPage = logInAndOpen(page, group);

        releaseGroupPage.editions().first().click();

        assertThat(page).hasURL(Pattern.compile("/releases/" + original.releaseId() + "$"));
        assertThat(new ReleasePage(page).title()).hasText("Hopes and Fears");
    }

    @Test
    void aReleaseGroupThatIsntInTheLibraryIsReported(final Page page) {
        new LoginPage(page).logIn("alice", "alice");
        assertThat(page).hasURL(Pattern.compile("/discover$"));
        final var releaseGroupPage = new ReleaseGroupPage(page);

        releaseGroupPage.open(uuidv7());

        assertThat(releaseGroupPage.message()).hasText("This release group isn't in the library.");
    }

    private ReleaseGroupPage logInAndOpen(final Page page, final ReleaseGroup group) {
        new LoginPage(page).logIn("alice", "alice");
        // Opening the page before the login finishes would reload it without a session.
        assertThat(page).hasURL(Pattern.compile("/discover$"));
        final var releaseGroupPage = new ReleaseGroupPage(page);
        releaseGroupPage.open(group.releaseGroupId());
        return releaseGroupPage;
    }

    private Release insertRelease(final ReleaseGroup group, final String name, final LocalDate date) {
        return releaseDao.upsert(
                new Release(uuidv7(), name, null, date, "GB", 0, group.releaseGroupId(), group.artistId(), uuidv7()));
    }

    private Release insertReleaseWithTrack(
            final ReleaseGroup group, final Artist artist, final String name, final LocalDate date) {
        final var release = insertRelease(group, name, date);
        insertTrack(insertMedia(release, "CD").getFirst(), artist, name);
        return release;
    }

    private Label insertLabel(final String name) {
        return releaseDao
                .upsertLabels(List.of(new Label(uuidv7(), name, uuidv7())))
                .getFirst();
    }

    private void linkLabels(final Release release, final Label... labels) {
        releaseDao.upsertReleaseLabels(Arrays.stream(labels)
                .map(label -> new ReleaseLabel(release.releaseId(), label.labelId()))
                .toList());
    }

    private List<Medium> insertMedia(final Release release, final String... formats) {
        final var media = IntStream.range(0, formats.length)
                .mapToObj(index -> new Medium(uuidv7(), release.releaseId(), index + 1, formats[index]))
                .toList();
        return releaseDao.upsertMedia(media);
    }

    private void insertTrack(final Medium medium, final Artist artist, final String name) {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(
                uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId(), IngestGroupState.MATCHED);
        ingestDao.insertGroup(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), name + ".flac", IngestState.COMPLETED);
        ingestDao.insertTrackIngest(ingest);
        trackDao.insert(new Track(
                uuidv7(),
                name,
                300,
                AudioFormat.FLAC,
                name + ".flac",
                medium.mediumId(),
                1,
                artist.artistId(),
                ingest.trackIngestId()));
    }
}
