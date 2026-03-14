package org.sona.library;

import com.google.common.jimfs.Jimfs;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sona.db.TrackToIngestDao;
import org.sona.model.tables.records.TrackToIngestRecord;
import org.sona.samples.FlacSample;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.sona.utils.IDGenerator.uuidv7;

@RequiredArgsConstructor
class DefaultLibraryTest {

    private final FileSystem fileSystem = Jimfs.newFileSystem();
    private final Path ingestFolder = fileSystem.getPath("/ingest");
    private final Path mediaFolder = fileSystem.getPath("/media");

    private final TrackToIngestDao trackToIngestDao = mock(TrackToIngestDao.class);

    private final DefaultLibrary library = new DefaultLibrary(trackToIngestDao, ingestFolder, mediaFolder);

    @Test
    void storeIngestTrack() throws IOException {
        final var groupId = uuidv7();
        final var sample = FlacSample.SAMPLE_3;

        ArgumentCaptor<List<TrackToIngestRecord>> captor = ArgumentCaptor.captor();

        library.storeIngestTrack(groupId, sample.fileName(), sample.inputStream());

        verify(trackToIngestDao).store(captor.capture());

        final var record = captor.getValue().getFirst();
    }
}