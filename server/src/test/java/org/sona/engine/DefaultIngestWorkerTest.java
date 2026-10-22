package org.sona.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.db.IngestGroupDao;
import org.sona.db.ReleaseDao;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.exception.InvalidFormatException;
import org.sona.library.Library;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.TrackToIngest;
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
    IngestGroupDao ingestGroupDao;

    @Autowired
    TrackToIngestDao trackToIngestDao;

    @Autowired
    TrackDao trackDao;

    @Autowired
    ReleaseDao releaseDao;

    @Test
    void aPassImportsEveryPendingIngest() throws InvalidFormatException, IOException {
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
    void aPassWithNothingPendingDoesNothing() throws InvalidFormatException, IOException {
        final var ingest = storeIngest(flacFile(whisperMetadata()));
        worker.processPending();

        worker.processPending();

        assertThat(reloadState(ingest)).isEqualTo(IngestState.COMPLETED);
    }

    @Test
    void aFileMusicBrainzDoesNotKnowNeedsInput() throws InvalidFormatException, IOException {
        final var ingest = storeIngest(flacFile(metadata("Zzyzx Qwerty Nonexistent Song", "Nobody Atall")));

        worker.processPending();

        assertThat(reloadState(ingest)).isEqualTo(IngestState.INPUT_REQUIRED);
    }

    @Test
    void aFileThatIsNotFlacFails() throws InvalidFormatException, IOException {
        final var ingest = storeIngest(new byte[] {1, 2, 3, 4});

        worker.processPending();

        assertThat(reloadState(ingest)).isEqualTo(IngestState.FAILED);
    }

    @Test
    void aFileWhoseLookupFailsFailsWithoutStoppingThePass() throws InvalidFormatException, IOException {
        // MusicBrainz isn't stubbed for this track, so looking it up answers 404.
        final var unstubbed = storeIngest(flacFile(metadata("Unstubbed Song", "Unstubbed Artist")));
        final var whisper = storeIngest(flacFile(whisperMetadata()));

        worker.processPending();

        assertThat(reloadState(unstubbed)).isEqualTo(IngestState.FAILED);
        assertThat(reloadState(whisper)).isEqualTo(IngestState.COMPLETED);
    }

    private TrackToIngest storeIngest(final byte[] file) throws InvalidFormatException, IOException {
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "A Rush of Blood to the Head");
        ingestGroupDao.upsert(group);
        return library.storeIngestTrack(group, "track.flac", new ByteArrayInputStream(file));
    }

    private IngestState reloadState(final TrackToIngest ingest) {
        return trackToIngestDao.loadById(ingest.trackIngestId()).state();
    }

    private static RawMetadata whisperMetadata() {
        final var metadata = metadata("A Whisper", "Coldplay");
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(9));
        return metadata;
    }

    private static RawMetadata metadata(final String title, final String artist) {
        final var metadata = new RawMetadata(Duration.ofSeconds(10));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str(title));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str(artist));
        return metadata;
    }
}
