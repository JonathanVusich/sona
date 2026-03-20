package org.sona.library;

import lombok.RequiredArgsConstructor;
import org.sona.config.properties.LibraryProperties;
import org.sona.db.TrackToIngestDao;
import org.sona.model.Track;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Library structure:
 *   ingest_folder -
 *     ingest[uuid] -
 *       track_x.[flac,mp3,wma]
 *   library_folder -
 *     release_group[mbid] -
 *       release[mbid] -
 *         track[mbid].[flac,mp3,wma]
 */
@Service
@RequiredArgsConstructor
public final class DefaultLibrary implements Library {

    private final TrackToIngestDao trackIngestDao;
    private final LibraryProperties config;

    @Override
    public void storeIngestTrack(final UUID ingestGroup,
                                 final String filename,
                                 final InputStream inputStream) throws IOException  {
        final UUID ingestTrackId = uuidv7();

        final var ingestFolder = config.ingestFolder()
                .resolve(ingestGroup.toString());

        Files.createDirectories(ingestFolder);

        final var ingestTarget = ingestFolder
                .resolve(ingestTrackId.toString());

        Files.copy(inputStream, ingestTarget);

        final var trackIngest = new TrackToIngest(
                ingestTrackId,
                ingestGroup,
                filename,
                IngestState.PENDING
        );

        trackIngestDao.store(List.of(trackIngest));
    }

    @Override
    public InputStream readIngestTrack(final TrackToIngest trackIngest) throws IOException {
        final var trackPath = resolveIngestTrack(trackIngest);
        return Files.newInputStream(trackPath, StandardOpenOption.READ);
    }

    @Override
    public void importTrack(final TrackToIngest trackIngest, final Track metadata) throws IOException {
        final var trackPath = resolveIngestTrack(trackIngest);
        final var libraryPath = resolveLibraryTrack(metadata);

        Files.copy(trackPath, libraryPath, StandardCopyOption.COPY_ATTRIBUTES);
    }

    @Override
    public InputStream readTrack(final Track track) throws IOException {
        final var libraryPath = resolveLibraryTrack(track);
        return Files.newInputStream(libraryPath, StandardOpenOption.READ);
    }

    private Path resolveLibraryTrack(final Track trackMetadata) {
        return new PathSegment(trackMetadata.releaseId(), trackMetadata.releaseName()).path();
    }

    private Path resolveIngestTrack(TrackToIngest trackIngest) {
        return config.ingestFolder().resolve(trackIngest.groupIngestId().toString()).resolve(trackIngest.trackIngestId().toString());
    }
}
