package org.sona.library;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.db.TrackToIngestDao;
import org.sona.format.Format;
import org.sona.metadata.RawMetadata;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class DefaultLibraryTest {

    private static final ReleaseGroup RELEASE_GROUP = new ReleaseGroup(
            UUID.fromString("01a11a00-0000-7000-8000-000000000001"),
            "A Rush of Blood: Deluxe/Remastered",
            UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf")
    );
    private static final Release RELEASE = new Release(
            UUID.fromString("01a11a00-0000-7000-8000-000000000002"),
            "A Rush of Blood to the Head",
            null,
            RELEASE_GROUP.releaseGroupId(),
            null,
            UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0")
    );
    private static final UUID TRACK_ID = UUID.fromString("01a11a00-0000-7000-8000-000000000003");

    @Autowired
    private DefaultLibrary library;
    @Autowired
    private TrackToIngestDao trackToIngestDao;

    @Test
    void readAndStoreIngestTrack() throws IOException {
        final var groupId = uuidv7();
        final var bytes = flacFile(new RawMetadata(Duration.ofSeconds(10)));

        library.storeIngestTrack(groupId, "track.flac", new ByteArrayInputStream(bytes));

        final var ingestTrack = trackToIngestDao.loadByGroup(groupId).getFirst();
        assertThat(ingestTrack.originalFileName()).isEqualTo("track.flac");
        assertThat(ingestTrack.state()).isEqualTo(IngestState.PENDING);

        try (final var stored = library.readIngestTrack(ingestTrack)) {
            assertThat(stored.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void libraryPathUsesDatabaseIds() {
        assertThat(library.libraryPath(RELEASE_GROUP, RELEASE, TRACK_ID, "A Whisper", Format.FLAC)).isEqualTo(
                "01a11a00-0000-7000-8000-000000000001[A Rush of Blood_ Deluxe_Remastered]/"
                        + "01a11a00-0000-7000-8000-000000000002[A Rush of Blood to the Head]/"
                        + "01a11a00-0000-7000-8000-000000000003[A Whisper].flac");
    }

    @Test
    void importTrackMovesFileIntoLibrary() throws IOException {
        final var bytes = flacFile(new RawMetadata(Duration.ofSeconds(10)));
        final var groupId = uuidv7();
        library.storeIngestTrack(groupId, "track.flac", new ByteArrayInputStream(bytes));
        final var ingestTrack = trackToIngestDao.loadByGroup(groupId).getFirst();
        final var path = library.libraryPath(RELEASE_GROUP, RELEASE, TRACK_ID, "A Whisper", Format.FLAC);

        library.importTrack(ingestTrack, path);

        try (final var imported = library.readTrack(track(path, ingestTrack.trackIngestId()))) {
            assertThat(imported.readAllBytes()).isEqualTo(bytes);
        }
        assertThatThrownBy(() -> library.readIngestTrack(ingestTrack)).isInstanceOf(NoSuchFileException.class);
    }

    private static Track track(final String path, final UUID trackIngestId) {
        return new Track(TRACK_ID, "A Whisper", 10, AudioFormat.FLAC, path, null, null, trackIngestId);
    }
}
