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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@E2ETest
class ReleaseGroupE2ETest {

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
    void aReleaseGroupListsItsEditionsEarliestFirst(final Page page) {
        final var keane = artistDao.upsert(new Artist(uuidv7(), "Keane", "GB", null, uuidv7()));
        final var group = releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "Hopes and Fears", ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10), keane.artistId(), uuidv7()));
        final var island = insertLabel("Island");
        final var deluxe = insertRelease(group, "Hopes and Fears (Deluxe Edition)", LocalDate.of(2014, 5, 12));
        insertMedia(deluxe, "CD", "CD");
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
                .hasText(new String[]{"Hopes and Fears", "Hopes and Fears (Deluxe Edition)"});
        assertThat(releaseGroupPage.editionDetails()).hasText(new String[]{
                "2004 · CD · Interscope, Island · GB · 1 song",
                "2014 · 2×CD · Island · GB · 0 songs"});
    }

    @Test
    void anEditionOpensItsReleasesPage(final Page page) {
        final var keane = artistDao.upsert(new Artist(uuidv7(), "Keane", "GB", null, uuidv7()));
        final var group = releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "Hopes and Fears", ReleaseGroupType.ALBUM,
                LocalDate.of(2004, 5, 10), keane.artistId(), uuidv7()));
        final var original = insertRelease(group, "Hopes and Fears", LocalDate.of(2004, 5, 10));
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

    private void insertTrack(final Medium medium, final Artist artist, final String name) {
        final var ingest = new TrackToIngest(uuidv7(), uuidv7(), name + ".flac", IngestState.COMPLETED);
        trackToIngestDao.insert(ingest);
        trackDao.insert(new Track(uuidv7(), name, 300, AudioFormat.FLAC, name + ".flac", medium.mediumId(), 1,
                artist.artistId(), ingest.trackIngestId()));
    }
}
