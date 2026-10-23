package org.sona.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.FilePayload;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.db.IngestGroupDao;
import org.sona.db.PasswordDao;
import org.sona.db.TrackToIngestDao;
import org.sona.db.UserDao;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@E2ETest
class UploadE2ETest {

    // Sona checks only an upload's extension when it stores it; the ingest worker, which reads the file, is off in
    // tests.
    private static final byte[] AUDIO = {1, 2, 3};

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordDao passwordDao;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    TrackToIngestDao trackToIngestDao;

    @Autowired
    IngestGroupDao ingestGroupDao;

    Users root;

    @BeforeEach
    void setUp() {
        root = new TestUsers(userDao, passwordDao, encoder).createUser("root", UserRole.ADMIN);
    }

    @Test
    void aFolderIsUploadedAsAGroupNamedAfterIt(final Page page) throws IOException {
        // Playwright names each file of a folder by its path relative to the folder, with the separator of the folder's
        // file system, and the browser keeps a Windows backslash as part of the name. A Unix file system gives the
        // browser the paths it gets on any machine.
        final var files = Jimfs.newFileSystem(Configuration.unix());
        final var album = Files.createDirectories(files.getPath("/Keane/Hopes and Fears"));
        Files.write(album.resolve("02 Bend and Break.flac"), AUDIO);
        Files.write(album.resolve("01 Somewhere Only We Know.flac"), AUDIO);
        final var uploadPage = logInAndOpen(page);

        uploadPage.chooseFolder(files.getPath("/Keane"));

        assertThat(uploadPage.groupFiles("Hopes and Fears")).hasText(new String[] {
            "Keane/Hopes and Fears/01 Somewhere Only We Know.flac", "Keane/Hopes and Fears/02 Bend and Break.flac"
        });
        uploadPage.uploadButton().click();

        assertThat(uploadPage.summary()).hasText("2 of 2 files uploaded");
        assertThat(uploadPage.status("01 Somewhere Only We Know.flac")).hasText("Uploaded");
        assertThat(storedGroup())
                .isEqualTo(new IngestGroup(null, IngestGroupKind.ALBUM, "Hopes and Fears", root.userId()));
    }

    @Test
    void looseFilesAreUploadedAsOneUnsortedGroup(final Page page) {
        final var uploadPage = logInAndOpen(page);

        uploadPage.chooseFiles(flac("Somewhere Only We Know.flac"), flac("Everybody's Changing.flac"));

        assertThat(uploadPage.groupFiles("Loose files"))
                .hasText(new String[] {"Everybody's Changing.flac", "Somewhere Only We Know.flac"});
        assertThat(uploadPage.group("Loose files")).containsText("Unsorted");
        uploadPage.uploadButton().click();

        assertThat(uploadPage.summary()).hasText("2 of 2 files uploaded");
        assertThat(storedGroup())
                .isEqualTo(new IngestGroup(null, IngestGroupKind.UNKNOWN, "Loose files", root.userId()));
    }

    @Test
    void aFileMovedIntoANewGroupIsUploadedInIt(final Page page) {
        final var uploadPage = logInAndOpen(page);
        uploadPage.chooseFiles(flac("Somewhere Only We Know.flac"), flac("Everybody's Changing.flac"));

        uploadPage.moveFile("Everybody's Changing.flac", "New group");
        uploadPage.renameGroup("New group", "Singles");

        assertThat(uploadPage.groupFiles("Loose files")).hasText(new String[] {"Somewhere Only We Know.flac"});
        assertThat(uploadPage.groupFiles("Singles")).hasText(new String[] {"Everybody's Changing.flac"});
        uploadPage.uploadButton().click();

        assertThat(uploadPage.summary()).hasText("2 of 2 files uploaded");
        assertThat(uploadPage.groupFiles("Singles")).hasText(new String[] {"Everybody's Changing.flac"});
        assertThat(uploadPage.status("Everybody's Changing.flac")).hasText("Uploaded");
    }

    @Test
    void aGroupMovedIntoAnotherJoinsIt(final Page page) {
        final var uploadPage = logInAndOpen(page);
        uploadPage.chooseFiles(flac("Somewhere Only We Know.flac"), flac("Everybody's Changing.flac"));
        uploadPage.moveFile("Everybody's Changing.flac", "New group");

        uploadPage.moveAllFiles("New group", "Loose files");

        assertThat(uploadPage.group("New group")).hasCount(0);
        assertThat(uploadPage.groupFiles("Loose files"))
                .hasText(new String[] {"Everybody's Changing.flac", "Somewhere Only We Know.flac"});
    }

    @Test
    void aFileLargerThanSonaAcceptsIsLeftOut(final Page page) {
        final var uploadPage = logInAndOpen(page);
        // One byte over the 10MB the tests' library accepts.
        final var tooLarge = new FilePayload("Live at Brixton.flac", "audio/flac", new byte[10 * 1024 * 1024 + 1]);

        uploadPage.chooseFiles(tooLarge, flac("Somewhere Only We Know.flac"));

        assertThat(uploadPage.error()).hasText("Left out 1 file larger than 10 MB: Live at Brixton.flac");
        assertThat(uploadPage.groupFiles("Loose files")).hasText(new String[] {"Somewhere Only We Know.flac"});
    }

    @Test
    void aGroupWithoutANameCantBeUploaded(final Page page) {
        final var uploadPage = logInAndOpen(page);
        uploadPage.chooseFiles(flac("Somewhere Only We Know.flac"));

        uploadPage.renameGroup("Loose files", "");

        assertThat(uploadPage.uploadButton()).isDisabled();
        assertThat(uploadPage.error()).hasText("Give every group a name.");
    }

    @Test
    void aFileInAFormatSonaCantIngestIsLeftOut(final Page page) {
        final var uploadPage = logInAndOpen(page);

        uploadPage.chooseFiles(
                new FilePayload("Somewhere Only We Know.mp3", "audio/mpeg", AUDIO), flac("Everybody's Changing.flac"));

        assertThat(uploadPage.error()).hasText("Left out 1 file Sona can't ingest yet: Somewhere Only We Know.mp3");
        assertThat(uploadPage.groupFiles("Loose files")).hasText(new String[] {"Everybody's Changing.flac"});
    }

    @Test
    void aFileWhoseConnectionBrokeCanBeSentAgain(final Page page) {
        final var uploadPage = logInAndOpen(page);
        uploadPage.chooseFiles(flac("Somewhere Only We Know.flac"));
        final Predicate<String> trackUploads = url -> url.contains("/tracks?");
        page.route(trackUploads, Route::abort);

        uploadPage.uploadButton().click();

        assertThat(uploadPage.status("Somewhere Only We Know.flac"))
                .hasText("Can't reach Sona. Check your connection and try again.");
        page.unroute(trackUploads);
        uploadPage.retryButton("Somewhere Only We Know.flac").click();

        assertThat(uploadPage.status("Somewhere Only We Know.flac")).hasText("Uploaded");
        assertThat(storedGroup())
                .isEqualTo(new IngestGroup(null, IngestGroupKind.UNKNOWN, "Loose files", root.userId()));
    }

    private UploadPage logInAndOpen(final Page page) {
        new LoginPage(page).logIn("root", "root");
        // Opening the page before the login finishes would reload it without a session.
        assertThat(page).hasURL(Pattern.compile("/discover$"));
        final var uploadPage = new UploadPage(page);
        uploadPage.open();
        return uploadPage;
    }

    // The group of the first file uploaded, with its ID, which the frontend made up, left out.
    private IngestGroup storedGroup() {
        final var ingest = trackToIngestDao.loadOldestPending().orElseThrow();
        final var group = ingestGroupDao.loadById(ingest.groupIngestId()).orElseThrow();
        return new IngestGroup(null, group.kind(), group.name(), group.userId());
    }

    private static FilePayload flac(final String name) {
        return new FilePayload(name, "audio/flac", AUDIO);
    }
}
