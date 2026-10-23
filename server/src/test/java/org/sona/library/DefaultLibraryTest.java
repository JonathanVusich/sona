package org.sona.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.config.properties.LibraryProperties;
import org.sona.db.IngestGroupDao;
import org.sona.db.TrackToIngestDao;
import org.sona.db.UserDao;
import org.sona.exception.InvalidFormatException;
import org.sona.format.Format;
import org.sona.metadata.RawMetadata;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class DefaultLibraryTest {

    private static final ReleaseGroup RELEASE_GROUP = new ReleaseGroup(
            UUID.fromString("01a11a00-0000-7000-8000-000000000001"),
            "A Rush of Blood: Deluxe/Remastered",
            null,
            null,
            null,
            0,
            UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf"));
    private static final Release RELEASE = new Release(
            UUID.fromString("01a11a00-0000-7000-8000-000000000002"),
            "A Rush of Blood to the Head",
            null,
            null,
            null,
            0,
            RELEASE_GROUP.releaseGroupId(),
            null,
            UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0"));
    private static final UUID TRACK_ID = UUID.fromString("01a11a00-0000-7000-8000-000000000003");

    @Autowired
    private DefaultLibrary library;

    @Autowired
    private IngestGroupDao ingestGroupDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private TrackToIngestDao trackToIngestDao;

    @Autowired
    private LibraryProperties libraryProperties;

    @Test
    void readAndStoreIngestTrack() throws InvalidFormatException, IOException {
        final var group = storeGroup();
        final var bytes = flacFile(new RawMetadata(Duration.ofSeconds(10)));

        final var ingestTrack = library.storeIngestTrack(group, "track.flac", new ByteArrayInputStream(bytes));

        assertThat(trackToIngestDao.loadById(ingestTrack.trackIngestId()))
                .isEqualTo(new TrackToIngest(
                        ingestTrack.trackIngestId(), group.ingestGroupId(), "track.flac", IngestState.PENDING));

        try (final var stored = library.readIngestTrack(ingestTrack)) {
            assertThat(stored.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void aFileInAnUnsupportedFormatIsNotStored() {
        final var group = storeGroup();
        final var file = new ByteArrayInputStream(new byte[] {1, 2, 3});

        assertThatThrownBy(() -> library.storeIngestTrack(group, "track.mp3", file))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessage("Extension mp3 is not supported!");
        assertThat(Files.exists(libraryProperties
                        .ingestFolder()
                        .resolve(group.ingestGroupId().toString())))
                .isFalse();
    }

    @Test
    void libraryPathUsesDatabaseIds() {
        assertThat(library.libraryPath(RELEASE_GROUP, RELEASE, TRACK_ID, "A Whisper", Format.FLAC))
                .isEqualTo("01a11a00-0000-7000-8000-000000000001[A Rush of Blood_ Deluxe_Remastered]/"
                        + "01a11a00-0000-7000-8000-000000000002[A Rush of Blood to the Head]/"
                        + "01a11a00-0000-7000-8000-000000000003[A Whisper].flac");
    }

    @Test
    void importTrackMovesFileIntoLibrary() throws InvalidFormatException, IOException {
        final var bytes = flacFile(new RawMetadata(Duration.ofSeconds(10)));
        final var ingestTrack = library.storeIngestTrack(storeGroup(), "track.flac", new ByteArrayInputStream(bytes));
        final var path = library.libraryPath(RELEASE_GROUP, RELEASE, TRACK_ID, "A Whisper", Format.FLAC);

        library.importTrack(ingestTrack, path);

        try (final var imported = library.readTrack(newTrack(path, ingestTrack.trackIngestId()))) {
            assertThat(imported.readAllBytes()).isEqualTo(bytes);
        }
        assertThatThrownBy(() -> library.readIngestTrack(ingestTrack)).isInstanceOf(NoSuchFileException.class);
    }

    private IngestGroup storeGroup() {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group =
                new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "A Rush of Blood to the Head", uploader.userId());
        ingestGroupDao.insert(group);
        return group;
    }

    private static Track newTrack(final String path, final UUID trackIngestId) {
        return new Track(TRACK_ID, "A Whisper", 10, AudioFormat.FLAC, path, null, 9, null, trackIngestId);
    }
}
