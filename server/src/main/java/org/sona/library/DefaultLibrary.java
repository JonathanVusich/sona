package org.sona.library;

import lombok.RequiredArgsConstructor;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.daos.TrackIngestDao;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackIngest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.stream.Stream;

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
@RequiredArgsConstructor
@Service
public final class DefaultLibrary implements Library {

    private final TrackIngestDao trackIngestDao;

    private final Path ingestFolder;
    private final Path mediaFolder;

    @Override
    public void storeIngestTrack(final UUID ingestGroup,
                                 final String filename,
                                 final InputStream inputStream) throws IOException  {
        final UUID ingestTrackId = uuidv7();

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
    }

    @Override
    public InputStream readIngestTrack(final TrackIngest trackIngest) throws IOException {
        final var trackPath = resolveIngestTrack(trackIngest);
        try (final var stream = Files.newInputStream(trackPath, StandardOpenOption.READ)) {
            return stream;
        }
    }

    @Override
    public void importTrack(final TrackIngest trackIngest, final Track metadata) throws IOException {
        final var trackPath = resolveIngestTrack(trackIngest);
        final var libraryPath = resolveLibraryTrack(metadata);

        Files.copy(trackPath, libraryPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.ATOMIC_MOVE);
    }

    private Path resolveLibraryTrack(final Track trackMetadata) {
        final var releasePath = new PathSegment(trackMetadata.releaseId(), trackMetadata());

        final var trackPath = new PathSegment(trackMetadata.mbReleaseTrack(), trackMetadata.mbReleaseTrackName());

        return Stream.of(releasePath, trackPath)
                .map(PathSegment::path)
                .reduce(Path::resolve)
                .orElseThrow();
    }

    private Path resolveIngestTrack(TrackIngest trackIngest) {
        return ingestFolder.resolve(trackIngest.groupIngestId().toString()).resolve(trackIngest.trackIngestId().toString());
    }

    private static Path formatPath(UUID uuid, String name) {
        return Path.of("%s[%s]".formatted(uuid, name));
    }
}
