package org.sona.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.samples.IngestUploadUtils.createAndUpload;
import static org.sona.samples.IngestUploadUtils.createIngest;
import static org.sona.samples.IngestUploadUtils.upload;
import static org.sona.utils.IDGenerator.uuidv7;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.db.IngestDao;
import org.sona.db.ReleaseDao;
import org.sona.db.TrackDao;
import org.sona.db.UserDao;
import org.sona.library.Library;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.RawMetadataBuilder;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestGroupState;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The worker's schedule is off in tests, so each test runs a pass itself.
 */
@IntegrationTest
class DefaultIngestWorkerTest {

    @Autowired
    IngestWorker worker;

    @Autowired
    Library library;

    @Autowired
    IngestDao ingestDao;

    @Autowired
    UserDao userDao;

    @Autowired
    TrackDao trackDao;

    @Autowired
    ReleaseDao releaseDao;

    @Test
    void aPassImportsEveryPendingIngest() throws IOException {
        final var first = storeIngest(flacFile(whisperMetadata()));
        final var second = storeIngest(flacFile(whisperMetadata()));

        worker.processPending();

        assertThat(reloadState(first)).isEqualTo(IngestState.COMPLETED);
        assertThat(reloadState(second)).isEqualTo(IngestState.COMPLETED);
        assertThat(trackDao.loadByIngest(first.trackIngestId()).name()).isEqualTo("A Whisper");
        assertThat(releaseDao.loadByMusicBrainzId(UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0")))
                .isPresent();
    }

    @Test
    void aPassWithNothingPendingDoesNothing() throws IOException {
        final var ingest = storeIngest(flacFile(whisperMetadata()));
        worker.processPending();

        worker.processPending();

        assertThat(reloadState(ingest)).isEqualTo(IngestState.COMPLETED);
    }

    @Test
    void aFileMusicBrainzDoesNotKnowNeedsInput() throws IOException {
        final var ingest = storeIngest(flacFile(metadata("Zzyzx Qwerty Nonexistent Song", "Nobody Atall")));

        worker.processPending();

        assertThat(reloadState(ingest)).isEqualTo(IngestState.INPUT_REQUIRED);
    }

    @Test
    void aFileThatIsNotFlacFails() throws IOException {
        final var ingest = storeIngest(new byte[] {1, 2, 3, 4});

        worker.processPending();

        assertThat(reloadState(ingest)).isEqualTo(IngestState.FAILED);
    }

    @Test
    void aFileWhoseLookupFailsFailsWithoutStoppingThePass() throws IOException {
        // MusicBrainz isn't stubbed for this track, so looking it up answers 404.
        final var unstubbed = storeIngest(flacFile(metadata("Unstubbed Song", "Unstubbed Artist")));
        final var whisper = storeIngest(flacFile(whisperMetadata()));

        worker.processPending();

        assertThat(reloadState(unstubbed)).isEqualTo(IngestState.FAILED);
        assertThat(reloadState(whisper)).isEqualTo(IngestState.COMPLETED);
    }

    @Test
    void anAlbumWaitsUntilAllItsFilesAreUploaded() throws IOException {
        final var group = storeGroup(IngestGroupKind.ALBUM);
        final var first = createIngest(ingestDao, group, "track.flac");
        final var second = createIngest(ingestDao, group, "track.flac");
        upload(library, first, flacFile(whisperMetadata()));

        worker.processPending();

        assertThat(reloadState(first)).isEqualTo(IngestState.UPLOADED);

        upload(library, second, flacFile(whisperMetadata()));
        worker.processPending();

        assertThat(reloadState(first)).isEqualTo(IngestState.COMPLETED);
        assertThat(reloadState(second)).isEqualTo(IngestState.COMPLETED);
    }

    @Test
    void anAlbumWithAFileThatIsNotFlacStillImportsTheRest() throws IOException {
        final var group = storeGroup(IngestGroupKind.ALBUM);
        final var broken = createAndUpload(ingestDao, library, group, "track.flac", new byte[] {1, 2, 3, 4});
        final var whisper = createAndUpload(ingestDao, library, group, "track.flac", flacFile(whisperMetadata()));

        worker.processPending();

        assertThat(reloadState(broken)).isEqualTo(IngestState.FAILED);
        assertThat(reloadState(whisper)).isEqualTo(IngestState.COMPLETED);
    }

    @Test
    void looseFilesAreMatchedOnceAllAreUploaded() throws IOException {
        final var group = storeGroup(IngestGroupKind.UNKNOWN);
        final var first = createIngest(ingestDao, group, "track.flac");
        final var second = createIngest(ingestDao, group, "track.flac");
        upload(library, first, flacFile(whisperMetadata()));

        worker.processPending();

        assertThat(reloadState(first)).isEqualTo(IngestState.UPLOADED);

        upload(library, second, flacFile(whisperMetadata()));
        worker.processPending();

        assertThat(reloadState(first)).isEqualTo(IngestState.COMPLETED);
        assertThat(reloadState(second)).isEqualTo(IngestState.COMPLETED);
    }

    private IngestGroup storeGroup(final IngestGroupKind kind) {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(
                uuidv7(), kind, "A Rush of Blood to the Head", uploader.userId(), IngestGroupState.CREATED);
        ingestDao.insertGroup(group);
        return group;
    }

    private TrackToIngest storeIngest(final byte[] file) throws IOException {
        final var group = storeGroup(IngestGroupKind.ALBUM);
        return createAndUpload(ingestDao, library, group, "track.flac", file);
    }

    private IngestState reloadState(final TrackToIngest ingest) {
        return ingestDao.loadTrackIngestById(ingest.trackIngestId()).state();
    }

    private static RawMetadata whisperMetadata() {
        final var metadata = new RawMetadataBuilder(Duration.ofSeconds(240));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str("A Whisper"));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str("Coldplay"));
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(9));
        return metadata.build();
    }

    private static RawMetadata metadata(final String title, final String artist) {
        final var metadata = new RawMetadataBuilder(Duration.ofSeconds(240));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str(title));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str(artist));
        return metadata.build();
    }
}
