package org.sona.library;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.sona.config.properties.LibraryProperties;
import org.sona.db.IngestDao;
import org.sona.exception.FileTooLargeException;
import org.sona.exception.IngestAlreadyUploadedException;
import org.sona.exception.IngestTrackNotFoundException;
import org.sona.exception.InvalidFormatException;
import org.sona.format.Format;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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

    private final IngestDao ingestDao;
    private final LibraryProperties config;

    @Override
    public void checkIngestFormat(final String filename) throws InvalidFormatException {
        final var extension = fileExtension(filename);
        if (!Format.supportedExtensions().contains(extension)) {
            throw new InvalidFormatException("Extension %s is not supported!".formatted(extension));
        }
    }

    @Override
    public TrackToIngest storeIngestTrack(
            final UUID ingestGroupId, final UUID ingestTrackId, final InputStream inputStream) throws IOException {
        final var trackIngest = ingestDao
                .loadGroupTrackIngest(ingestGroupId, ingestTrackId)
                .orElseThrow(() -> new IngestTrackNotFoundException(ingestGroupId, ingestTrackId));
        checkNotUploaded(trackIngest);
        ingestDao.updateState(ingestTrackId, IngestState.UPLOADING);

        final var ingestFolder = config.ingestFolder().resolve(ingestGroupId.toString());
        Files.createDirectories(ingestFolder);
        final var ingestTarget = ingestFolder.resolve(ingestTrackId.toString());

        try {
            copyUpload(inputStream, ingestTarget);
        } catch (IOException | FileTooLargeException e) {
            // An upload cut short leaves part of a file, which the next upload of it replaces.
            Files.deleteIfExists(ingestTarget);
            throw e;
        }

        ingestDao.updateUploaded(ingestTrackId);
        return ingestDao.loadTrackIngestById(ingestTrackId);
    }

    @Override
    public SeekableByteChannel readIngestTrack(final TrackToIngest trackIngest) throws IOException {
        final var trackPath = resolveIngestTrack(trackIngest);
        return Files.newByteChannel(trackPath, StandardOpenOption.READ);
    }

    @Override
    public String libraryPath(
            final ReleaseGroup releaseGroup,
            final Release release,
            final UUID trackId,
            final String trackName,
            final TrackToIngest trackIngest) {
        final var releaseGroupSegment = new PathSegment(releaseGroup.releaseGroupId(), releaseGroup.name());
        final var releaseSegment = new PathSegment(release.releaseId(), release.name());
        final var trackSegment = new PathSegment(trackId, trackName);

        return "%s/%s/%s.%s"
                .formatted(
                        releaseGroupSegment,
                        releaseSegment,
                        trackSegment,
                        fileExtension(trackIngest.originalFileName()));
    }

    @Override
    public void importTrack(final TrackToIngest trackIngest, final String libraryPath) throws IOException {
        final var trackPath = resolveIngestTrack(trackIngest);
        final var target = resolveLibraryPath(libraryPath);

        Files.createDirectories(target.getParent());
        Files.move(trackPath, target);
    }

    @Override
    public Resource trackFile(final Track track) {
        return new FileSystemResource(resolveLibraryPath(track.path()));
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

    private static String fileExtension(final String filename) {
        return FilenameUtils.getExtension(filename).toLowerCase(Locale.ROOT);
    }

    // A file is sent again while its ingest is created or uploading. Once it is uploaded it may be in the middle of
    // being matched, so the stored file mustn't be replaced.
    private static void checkNotUploaded(final TrackToIngest trackIngest) {
        switch (trackIngest.state()) {
            case CREATED, UPLOADING -> {}
            case UPLOADED, INPUT_REQUIRED, MOVE_FAILED, FAILED, COMPLETED ->
                throw new IngestAlreadyUploadedException(trackIngest.trackIngestId());
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
