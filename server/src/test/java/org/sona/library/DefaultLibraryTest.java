package org.sona.library;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.db.TrackToIngestDao;
import org.sona.samples.FlacSample;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class DefaultLibraryTest {

    @Autowired
    private DefaultLibrary library;
    @Autowired
    private TrackToIngestDao trackToIngestDao;

    @Test
    void storeIngestTrack() throws IOException {
        final var groupId = uuidv7();
        final var sample = FlacSample.SAMPLE_3;

        library.storeIngestTrack(groupId, sample.fileName(), sample.inputStream());

        final var ingestTrack = trackToIngestDao.loadByGroup(groupId).findFirst().orElseThrow();
    }
}