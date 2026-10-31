package org.sona.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.samples.FlacFiles.flacFile;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.FilePayload;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.controller.ApiRoutes;
import org.sona.db.IngestDao;
import org.sona.db.UserDao;
import org.sona.engine.IngestWorker;
import org.sona.metadata.RawMetadataBuilder;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Users;
import org.sona.samples.Mp3Sample;
import org.sona.samples.ShortRecordings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;

@E2ETest
class UploadE2ETest {

    // Sona checks only an upload's extension when it stores it; the ingest worker, which reads the file, is off in
    // tests, and only runs when a test runs a pass itself.
    private static final byte[] AUDIO = {1, 2, 3};

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    IngestDao ingestDao;

    @Autowired
    IngestWorker worker;

    @Autowired
    WireMockServer musicBrainz;

    @Autowired
    SimpUserRegistry userRegistry;

    Users root;

    @BeforeEach
    void setUp() {
        root = new TestUsers(userDao, encoder).createUser("root", UserRole.ADMIN);
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
        assertThat(uploadPage.status("01 Somewhere Only We Know.flac")).hasText("Waiting to be matched");
        assertThat(storedGroup())
                .isEqualTo(new IngestGroup(null, IngestGroupKind.ALBUM, "Hopes and Fears", root.userId(), 2));
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
                .isEqualTo(new IngestGroup(null, IngestGroupKind.UNKNOWN, "Loose files", root.userId(), 2));
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
        assertThat(uploadPage.status("Everybody's Changing.flac")).hasText("Waiting to be matched");
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

        assertThat(uploadPage.status("Somewhere Only We Know.flac")).hasText("Waiting to be matched");
        assertThat(storedGroup())
                .isEqualTo(new IngestGroup(null, IngestGroupKind.UNKNOWN, "Loose files", root.userId(), 1));
    }

    @Test
    void aFileShowsHowMuchOfItHasBeenSent(final Page page) {
        final var uploadPage = logInAndOpen(page);
        uploadPage.chooseFiles(flac("Somewhere Only We Know.flac"));
        final var held = new ArrayList<Route>();
        page.route(url -> url.contains("/tracks?"), held::add);

        uploadPage.uploadButton().click();
        // WebKit sends the request a while after the ring appears.
        page.waitForCondition(() -> !held.isEmpty());

        assertThat(uploadPage.progressBar("Somewhere Only We Know.flac")).isVisible();
        // The ring stands in for the status while the file is sent.
        assertThat(uploadPage.status("Somewhere Only We Know.flac")).hasCount(0);
        held.getFirst().resume();

        assertThat(uploadPage.progressBar("Somewhere Only We Know.flac")).hasCount(0);
        assertThat(uploadPage.status("Somewhere Only We Know.flac")).hasText("Waiting to be matched");
    }

    @Test
    void anUploadedFileIsShownAddedToTheLibrary(final Page page) throws IOException, InterruptedException {
        final var files = Jimfs.newFileSystem(Configuration.unix());
        final var album = Files.createDirectories(files.getPath("/Coldplay/Parachutes"));
        Files.write(album.resolve("09 A Whisper.flac"), whisper());
        final var uploadPage = logInAndOpen(page);
        uploadPage.chooseFolder(files.getPath("/Coldplay"));
        awaitProgressSubscription();

        uploadPage.uploadButton().click();
        assertThat(uploadPage.status("09 A Whisper.flac")).hasText("Waiting to be matched");
        worker.processPending();

        assertThat(uploadPage.status("09 A Whisper.flac")).hasText("Added to the library");
        assertThat(uploadPage.ingestSummary()).hasText("1 added to the library");
    }

    @Test
    void anUploadedMp3IsShownAddedToTheLibrary(final Page page) throws IOException, InterruptedException {
        final byte[] mp3;
        try (final var inputStream = Mp3Sample.STEREO_CBR_ID3V24.inputStream()) {
            mp3 = inputStream.readAllBytes();
        }
        final var uploadPage = logInAndOpen(page);
        uploadPage.chooseFiles(new FilePayload("09 A Whisper.mp3", "audio/mpeg", mp3));
        awaitProgressSubscription();

        uploadPage.uploadButton().click();
        assertThat(uploadPage.status("09 A Whisper.mp3")).hasText("Waiting to be matched");
        final var stubId = ShortRecordings.stubWhisperSearch(musicBrainz);
        try {
            worker.processPending();
        } finally {
            ShortRecordings.removeStub(musicBrainz, stubId);
        }

        assertThat(uploadPage.status("09 A Whisper.mp3")).hasText("Added to the library");
        assertThat(uploadPage.ingestSummary()).hasText("1 added to the library");
    }

    @Test
    void aFileSonaCantReadIsShownNotAdded(final Page page) throws InterruptedException {
        final var uploadPage = logInAndOpen(page);
        uploadPage.chooseFiles(flac("Somewhere Only We Know.flac"));
        awaitProgressSubscription();

        uploadPage.uploadButton().click();
        assertThat(uploadPage.status("Somewhere Only We Know.flac")).hasText("Waiting to be matched");
        worker.processPending();

        assertThat(uploadPage.status("Somewhere Only We Know.flac")).hasText("Couldn't be added");
        assertThat(uploadPage.ingestSummary()).hasText("0 added to the library");
    }

    // Sona sends no state it gave an ingest before the page subscribed, so a pass run sooner would go unseen.
    private void awaitProgressSubscription() throws InterruptedException {
        final var deadline = Instant.now().plusSeconds(5);
        while (!isSubscribed()) {
            assertThat(Instant.now()).as("subscribed by now").isBefore(deadline);
            Thread.sleep(10);
        }
    }

    private boolean isSubscribed() {
        final var socketUser = userRegistry.getUser(root.userId().toString());
        if (socketUser == null) {
            return false;
        }
        return socketUser.getSessions().stream()
                .flatMap(session -> session.getSubscriptions().stream())
                .anyMatch(subscription -> subscription.getDestination().equals(ApiRoutes.INGEST_PROGRESS));
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
        final var ingest = ingestDao.loadOldestReadyPending().orElseThrow();
        final var group = ingestDao.loadGroupById(ingest.groupIngestId()).orElseThrow();
        return new IngestGroup(null, group.kind(), group.name(), group.userId(), group.trackCount());
    }

    private static FilePayload flac(final String name) {
        return new FilePayload(name, "audio/flac", AUDIO);
    }

    // A track the recorded MusicBrainz responses match, uploaded from its album's folder.
    private static byte[] whisper() {
        final var metadata = new RawMetadataBuilder(Duration.ofSeconds(240));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str("A Whisper"));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str("Coldplay"));
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(9));
        return flacFile(metadata.build());
    }
}
