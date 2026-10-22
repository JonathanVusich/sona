package org.sona.library;

import static org.sona.utils.IDGenerator.uuidv7;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.sona.config.properties.LibraryProperties;
import org.sona.db.TrackToIngestDao;
import org.sona.exception.FileTooLargeException;
import org.sona.exception.InvalidFormatException;
import org.sona.format.Format;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Service;

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

    private final TrackToIngestDao trackToIngestDao;
    private final LibraryProperties config;

    @Override
    public TrackToIngest storeIngestTrack(
            final IngestGroup ingestGroup, final String filename, final InputStream inputStream)
            throws InvalidFormatException, IOException {
        checkFormat(filename);
        final UUID ingestTrackId = uuidv7();

        final var ingestFolder =
                config.ingestFolder().resolve(ingestGroup.ingestGroupId().toString());

        Files.createDirectories(ingestFolder);

        final var ingestTarget = ingestFolder.resolve(ingestTrackId.toString());

        try {
            copyUpload(inputStream, ingestTarget);
        } catch (IOException | FileTooLargeException e) {
            // An upload cut short leaves part of a file, which no ingest row would ever point at.
            Files.deleteIfExists(ingestTarget);
            throw e;
        }

        final var trackIngest =
                new TrackToIngest(ingestTrackId, ingestGroup.ingestGroupId(), filename, IngestState.PENDING);

        trackToIngestDao.insert(trackIngest);
        return trackIngest;
    }

    @Override
    public InputStream readIngestTrack(final TrackToIngest trackIngest) throws IOException {
        final var trackPath = resolveIngestTrack(trackIngest);
        return Files.newInputStream(trackPath, StandardOpenOption.READ);
    }

    @Override
    public String libraryPath(
            final ReleaseGroup releaseGroup,
            final Release release,
            final UUID trackId,
            final String trackName,
            final Format format) {
        final var releaseGroupSegment = new PathSegment(releaseGroup.releaseGroupId(), releaseGroup.name());
        final var releaseSegment = new PathSegment(release.releaseId(), release.name());
        final var trackSegment = new PathSegment(trackId, trackName);

        return "%s/%s/%s.%s"
                .formatted(
                        releaseGroupSegment,
                        releaseSegment,
                        trackSegment,
                        format.getExtensions().getFirst());
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

    // Reads one byte past the limit at most, so a file that's too large is refused without storing all of it.
    private void copyUpload(final InputStream inputStream, final Path target) throws IOException {
        final var maxBytes = config.maxUploadSize().toBytes();
        final var bounded = BoundedInputStream.builder()
                .setInputStream(inputStream)
                .setMaxCount(maxBytes + 1)
                .setPropagateClose(false)
                .get();
        final var copied = Files.copy(bounded, target);
        if (copied > maxBytes) {
            throw new FileTooLargeException(config.maxUploadSize());
        }
    }

    private static void checkFormat(final String filename) throws InvalidFormatException {
        final var extension = FilenameUtils.getExtension(filename).toLowerCase(Locale.ROOT);
        if (!Format.supportedExtensions().contains(extension)) {
            throw new InvalidFormatException("Extension %s is not supported!".formatted(extension));
        }
    }

    private Path resolveLibraryPath(final String libraryPath) {
        return config.mediaFolder().resolve(libraryPath);
    }

    private Path resolveIngestTrack(TrackToIngest trackIngest) {
        return config.ingestFolder()
                .resolve(trackIngest.groupIngestId().toString())
                .resolve(trackIngest.trackIngestId().toString());
    }
}
