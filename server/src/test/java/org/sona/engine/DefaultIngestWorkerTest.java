package org.sona.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sona.auth.TestUsers.insertUser;
import static org.sona.model.Tables.INGEST_GROUP;
import static org.sona.model.Tables.INGEST_GROUP_CANDIDATE;
import static org.sona.model.Tables.TRACK;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.samples.IngestUploadUtils.createAndUpload;
import static org.sona.samples.IngestUploadUtils.createIngest;
import static org.sona.samples.IngestUploadUtils.loadIngest;
import static org.sona.samples.IngestUploadUtils.upload;
import static org.sona.utils.IDGenerator.uuidv7;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.javax.sonaudio.metadata.RawMetadata;
import dev.javax.sonaudio.metadata.RawMetadataBuilder;
import dev.javax.sonaudio.metadata.Tag;
import dev.javax.sonaudio.metadata.TagValue;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.db.IngestDao;
import org.sona.db.ReleaseDao;
import org.sona.db.UserDao;
import org.sona.library.Library;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestGroupState;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.pojos.Users;
import org.sona.samples.TwoTrackAlbum;
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
    DSLContext dsl;

    @Autowired
    ReleaseDao releaseDao;

    @Autowired
    WireMockServer musicBrainz;

    @Test
    void aPassImportsEveryPendingIngest() throws IOException {
        final var first = storeIngest(flacFile(whisperMetadata()));
        final var second = storeIngest(flacFile(whisperMetadata()));

        worker.processPending();

        assertThat(reloadState(first)).isEqualTo(IngestState.COMPLETED);
        assertThat(reloadState(second)).isEqualTo(IngestState.COMPLETED);
        assertThat(dsl.fetchValue(TRACK.NAME, TRACK.TRACK_INGEST_ID.eq(first.trackIngestId())))
                .isEqualTo("A Whisper");
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
        upload(library, first, flacFile(metadata("Song One", "Coldplay")));

        worker.processPending();

        assertThat(reloadState(first)).isEqualTo(IngestState.UPLOADED);

        upload(library, second, flacFile(metadata("Song Two", "Coldplay")));
        processWithTwoTrackAlbum();

        assertThat(reloadState(first)).isEqualTo(IngestState.COMPLETED);
        assertThat(reloadState(second)).isEqualTo(IngestState.COMPLETED);
        assertThat(reloadGroupState(group)).isEqualTo(IngestGroupState.MATCHED);
    }

    @Test
    void anAlbumWithAFileThatIsNotFlacIsMatchedOnTheRest() throws IOException {
        final var group = storeGroup(IngestGroupKind.ALBUM);
        final var broken = createAndUpload(ingestDao, library, group, "track.flac", new byte[] {1, 2, 3, 4});
        final var songOne =
                createAndUpload(ingestDao, library, group, "track.flac", flacFile(metadata("Song One", "Coldplay")));
        final var songTwo =
                createAndUpload(ingestDao, library, group, "track.flac", flacFile(metadata("Song Two", "Coldplay")));

        // Two files are read, so the album of two tracks matches.
        processWithTwoTrackAlbum();

        assertThat(reloadState(broken)).isEqualTo(IngestState.FAILED);
        assertThat(reloadState(songOne)).isEqualTo(IngestState.COMPLETED);
        assertThat(reloadState(songTwo)).isEqualTo(IngestState.COMPLETED);
    }

    @Test
    void anAlbumNoReleaseMatchesWaitsForItsUploaderWithTheReleasesItMightBe() throws IOException {
        final var group = storeGroup(IngestGroupKind.ALBUM);
        final var song =
                createAndUpload(ingestDao, library, group, "track.flac", flacFile(metadata("Song One", "Coldplay")));
        final var unknown = createAndUpload(
                ingestDao,
                library,
                group,
                "track.flac",
                flacFile(metadata("Zzyzx Qwerty Nonexistent Song", "Nobody Atall")));

        processWithTwoTrackAlbum();

        assertThat(reloadState(song)).isEqualTo(IngestState.INPUT_REQUIRED);
        assertThat(reloadState(unknown)).isEqualTo(IngestState.INPUT_REQUIRED);
        assertThat(reloadGroupState(group)).isEqualTo(IngestGroupState.INPUT_REQUIRED);
        assertThat(dsl.select(INGEST_GROUP_CANDIDATE.MUSICBRAINZ_RELEASE_ID, INGEST_GROUP_CANDIDATE.PLACED_COUNT)
                        .from(INGEST_GROUP_CANDIDATE)
                        .where(INGEST_GROUP_CANDIDATE.INGEST_GROUP_ID.eq(group.ingestGroupId()))
                        .fetch(Records.mapping((releaseId, placedCount) -> tuple(releaseId, placedCount))))
                .containsExactly(tuple(TwoTrackAlbum.RELEASE_ID, 1));
    }

    private void processWithTwoTrackAlbum() {
        final var stubIds = TwoTrackAlbum.stub(musicBrainz);
        try {
            worker.processPending();
        } finally {
            // The stub server is shared with other tests.
            TwoTrackAlbum.removeStubs(musicBrainz, stubIds);
        }
    }

    private IngestGroupState reloadGroupState(final IngestGroup group) {
        return dsl.fetchValue(INGEST_GROUP.STATE, INGEST_GROUP.INGEST_GROUP_ID.eq(group.ingestGroupId()));
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
        insertUser(userDao, uploader);
        final var group = new IngestGroup(
                uuidv7(), kind, "A Rush of Blood to the Head", uploader.userId(), IngestGroupState.CREATED);
        ingestDao.insertGroup(group);
        return group;
    }

    // A loose file: an album of one is matched only on a release of one track.
    private TrackToIngest storeIngest(final byte[] file) throws IOException {
        final var group = storeGroup(IngestGroupKind.UNKNOWN);
        return createAndUpload(ingestDao, library, group, "track.flac", file);
    }

    private IngestState reloadState(final TrackToIngest ingest) {
        return loadIngest(dsl, ingest.trackIngestId()).state();
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
