package org.sona.library;

import lombok.RequiredArgsConstructor;
import org.sona.config.properties.LibraryProperties;
import org.sona.format.Format;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.store.IngestStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Library structure, named by Sona's database IDs (a track may have no MusicBrainz IDs):
 *   ingest_folder -
 *     [group uuid] -
 *       [track ingest uuid]
 *   media_folder -
 *     [release group id][name] -
 *       [release id][name] -
 *         [track id][title].[flac,mp3,wma]
 */
@Service
@RequiredArgsConstructor
public final class DefaultLibrary implements Library {

    private final IngestStore ingestStore;
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

        ingestStore.store(trackIngest);
    }

    @Override
    public InputStream readIngestTrack(final TrackToIngest trackIngest) throws IOException {
        final var trackPath = resolveIngestTrack(trackIngest);
        return Files.newInputStream(trackPath, StandardOpenOption.READ);
    }

    @Override
    public String libraryPath(final ReleaseGroup releaseGroup,
                              final Release release,
                              final UUID trackId,
                              final String trackName,
                              final Format format) {
        final var releaseGroupSegment = new PathSegment(releaseGroup.releaseGroupId(), releaseGroup.name());
        final var releaseSegment = new PathSegment(release.releaseId(), release.name());
        final var trackSegment = new PathSegment(trackId, trackName);

        return "%s/%s/%s.%s".formatted(releaseGroupSegment, releaseSegment, trackSegment, format.getExtensions().getFirst());
    }

    @Override
    public void importTrack(final TrackToIngest trackIngest, final String libraryPath) throws IOException {
        final var trackPath = resolveIngestTrack(trackIngest);
        final var target = resolveLibraryPath(libraryPath);

        Files.createDirectories(target.getParent());
        Files.move(trackPath, target);
    }

    @Override
    public InputStream readTrack(final Track track) throws IOException {
        return Files.newInputStream(resolveLibraryPath(track.path()), StandardOpenOption.READ);
    }

    private Path resolveLibraryPath(final String libraryPath) {
        return config.mediaFolder().resolve(libraryPath);
    }

    private Path resolveIngestTrack(TrackToIngest trackIngest) {
        return config.ingestFolder().resolve(trackIngest.groupIngestId().toString()).resolve(trackIngest.trackIngestId().toString());
    }
}
