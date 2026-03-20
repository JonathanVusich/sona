package org.sona.library;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.db.TrackToIngestDao;
import org.sona.model.Format;
import org.sona.model.Track;
import org.sona.model.enums.IngestState;
import org.sona.samples.FlacSample;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class DefaultLibraryTest {

    @Autowired
    private DefaultLibrary library;
    @Autowired
    private TrackToIngestDao trackToIngestDao;

    @Test
    void readAndStoreIngestTrack() throws IOException {
        final var groupId = uuidv7();
        final var sample = FlacSample.SAMPLE_3;

        library.storeIngestTrack(groupId, sample.fileName(), sample.inputStream());

        final var ingestTrack = trackToIngestDao.loadByGroup(groupId).findFirst().orElseThrow();

        assertThat(ingestTrack.getOriginalFileName()).isEqualTo(sample.fileName());
        assertThat(ingestTrack.getState()).isEqualTo(IngestState.PENDING);

        final var inputBytes = sample.inputStream().readAllBytes();
        final var sampleBytes = library.readIngestTrack(ingestTrack).readAllBytes();

        assertThat(inputBytes).isEqualTo(sampleBytes);
    }

    @Test
    void importTrack() throws IOException {
        final var groupId = uuidv7();
        final var sample = FlacSample.SAMPLE_3;
        library.storeIngestTrack(groupId, sample.fileName(), sample.inputStream());
        final var ingestTrack = trackToIngestDao.loadByGroup(groupId).findFirst().orElseThrow();

        final var track = new Track(
                uuidv7(),
                "Sample",
                Duration.ofSeconds(10),
                Format.FLAC,
                uuidv7(),
                null,
                uuidv7(),
                null);

        library.importTrack(ingestTrack, track);

        final var importedBytes = library.readTrack(track).readAllBytes();
        final var sampleBytes = sample.inputStream().readAllBytes();
        assertThat(importedBytes).isEqualTo(sampleBytes);
    }
}