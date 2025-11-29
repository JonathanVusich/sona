package org.sona.library;

import lombok.RequiredArgsConstructor;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.daos.TrackIngestDao;
import org.sona.model.tables.pojos.TrackIngest;
import org.sona.utils.ID;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Library structure:
 *   ingest_folder -
 *     ingest[uuid] -
 *       track_x.[flac,mp3,wma]
 *   library_folder -
 *     artist[mbid] -
 *       release_group[mbid] -
 *         release[mbid] -
 *           track[mbid].[flac,mp3,wma]
 */
@RequiredArgsConstructor
@Service
public final class DefaultLibrary implements Library {

    private final TrackIngestDao trackIngestDao;

    private final Path ingestFolder;
    private final Path mediaFolder;

    @Override
    public TrackIngest storeIngestTrack(final UUID ingestGroup,
                                        final String filename,
                                        final InputStream inputStream) throws IOException  {
        final UUID ingestTrackId = ID.v7();

        final var ingestTarget = ingestFolder
                .resolve(ingestGroup.toString())
                .resolve(ingestTrackId.toString());

        Files.copy(inputStream, ingestTarget);

        final var trackIngest = new TrackIngest(
                ingestTrackId,
                ingestGroup,
                filename,
                IngestState.PENDING
        );

        trackIngestDao.insert(trackIngest);

        return trackIngest;
    }

    @Override
    public InputStream readIngestTrack(final UUID ingestGroup, final UUID trackId) throws IOException {
        final var trackPath = ingestFolder.resolve(ingestGroup.toString()).resolve(trackId.toString());
        try (final var stream = Files.newInputStream(trackPath, StandardOpenOption.READ)) {
            return stream;
        }
    }
}
