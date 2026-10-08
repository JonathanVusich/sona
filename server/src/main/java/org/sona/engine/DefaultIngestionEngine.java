package org.sona.engine;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.sona.db.ArtistDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.exception.InvalidFormatException;
import org.sona.format.Format;
import org.sona.format.Parser;
import org.sona.format.flac.FlacParser;
import org.sona.library.Library;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.resolver.MetadataResolver;
import org.sona.metadata.resolver.ResolvedArtist;
import org.sona.metadata.resolver.ResolvedTrack;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;
import static org.sona.utils.IDGenerator.uuidv7;

@Service
@RequiredArgsConstructor
public final class DefaultIngestionEngine implements IngestionEngine {

    private static final Map<String, Parser> PARSERS = Stream.of(
            new FlacParser()
    ).flatMap(p -> p.format().getExtensions().stream().map(ext -> entry(ext, p)))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    private final Library library;
    private final MetadataResolver resolver;
    private final TrackToIngestDao trackToIngestDao;
    private final ArtistDao artistDao;
    private final ReleaseGroupDao releaseGroupDao;
    private final ReleaseDao releaseDao;
    private final TrackDao trackDao;

    @Override
    public void processTrack(final TrackToIngest trackToIngest) throws InvalidFormatException, IOException, InterruptedException {
        switch (trackToIngest.state()) {
            case PENDING, INPUT_REQUIRED -> ingestTrack(trackToIngest);
            case MOVE_FAILED -> retryMove(trackToIngest);
            case FAILED -> throw new IllegalStateException(
                    "Track %s can't be imported".formatted(trackToIngest.trackIngestId()));
            case COMPLETED -> throw new IllegalStateException(
                    "Track %s has already been imported".formatted(trackToIngest.trackIngestId()));
        }
    }

    private void ingestTrack(final TrackToIngest trackToIngest) throws InvalidFormatException, IOException, InterruptedException {
        final Parser parser;
        final RawMetadata metadata;
        try {
            parser = parserFor(trackToIngest);
            metadata = parseIngestTrack(trackToIngest, parser);
        } catch (InvalidFormatException e) {
            // Processing it again would fail the same way.
            markState(trackToIngest.trackIngestId(), IngestState.FAILED);
            throw e;
        }
        final var resolved = resolver.resolveTrack(trackToIngest, metadata)
                .orElse(null);

        // No MusicBrainz track info present!
        if (resolved == null) {
            markState(trackToIngest.trackIngestId(), IngestState.INPUT_REQUIRED);

            // TODO: Refactor to support multiple metadata providers using plugins
            // TODO: Implement metadata searching
            return;
        }

        importTrack(trackToIngest, resolved, metadata.duration(), parser.format());
    }

    private static Parser parserFor(final TrackToIngest trackToIngest) throws InvalidFormatException {
        final var extension = FilenameUtils.getExtension(trackToIngest.originalFileName()).toLowerCase(Locale.ROOT);
        final var parser = PARSERS.get(extension);
        if (parser == null) {
            throw new InvalidFormatException("Extension %s is not supported!".formatted(extension));
        }
        return parser;
    }

    private RawMetadata parseIngestTrack(final TrackToIngest trackToIngest,
                                          final Parser parser) throws IOException, InvalidFormatException {
        try (final var inputStream = library.readIngestTrack(trackToIngest)) {
            return parser.parse(inputStream);
        }
    }

    private void importTrack(final TrackToIngest trackToIngest,
                             final ResolvedTrack resolved,
                             final Duration duration,
                             final Format format) throws IOException {
        final var track = storeTrack(trackToIngest, resolved, duration, format);
        moveIntoLibrary(trackToIngest, track);
    }

    private void retryMove(final TrackToIngest trackToIngest) throws IOException {
        // The metadata was stored before the move failed, so only the move is retried.
        final var track = trackDao.loadByIngest(trackToIngest.trackIngestId());
        moveIntoLibrary(trackToIngest, track);
    }

    private void moveIntoLibrary(final TrackToIngest trackToIngest, final Track track) throws IOException {
        try {
            library.importTrack(trackToIngest, track.path());
        } catch (IOException e) {
            markState(trackToIngest.trackIngestId(), IngestState.MOVE_FAILED);
            throw e;
        }
        markState(trackToIngest.trackIngestId(), IngestState.COMPLETED);
    }

    private void markState(final UUID trackIngestId, final IngestState state) {
        trackToIngestDao.updateState(trackIngestId, state);
    }

    /**
     * Stores the track together with the artist, release group and release rows it references. Each row commits on
     * its own: the upserts are idempotent, so if the track insert fails, processing the ingest again reuses the rows.
     */
    private Track storeTrack(final TrackToIngest trackToIngest,
                             final ResolvedTrack resolved,
                             final Duration duration,
                             final Format format) {
        // Tracks from the same MusicBrainz release share its rows, and so its library folder.
        final var artist = upsertArtist(resolved.artist());
        final var releaseArtist = upsertArtist(resolved.releaseArtist());
        final var releaseGroup = releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), resolved.releaseGroupTitle(), resolved.releaseGroupId()));
        final var release = releaseDao.upsert(new Release(uuidv7(), resolved.releaseTitle(), null,
                releaseGroup.releaseGroupId(), releaseArtist.artistId(), resolved.releaseId()));
        final var track = newTrack(trackToIngest, resolved, artist, releaseGroup, release, duration, format);
        trackDao.insert(track);
        return track;
    }

    private Artist upsertArtist(final ResolvedArtist artist) {
        return artistDao.upsert(new Artist(uuidv7(), artist.name(), null, artist.artistId()));
    }

    private Track newTrack(final TrackToIngest trackToIngest,
                           final ResolvedTrack resolved,
                           final Artist artist,
                           final ReleaseGroup releaseGroup,
                           final Release release,
                           final Duration duration,
                           final Format format) {
        final var trackId = uuidv7();
        return new Track(
                trackId,
                resolved.title(),
                Math.toIntExact(duration.toSeconds()),
                audioFormat(format),
                library.libraryPath(releaseGroup, release, trackId, resolved.title(), format),
                artist.artistId(),
                release.releaseId(),
                trackToIngest.trackIngestId()
        );
    }

    private static AudioFormat audioFormat(final Format format) {
        return switch (format) {
            case FLAC -> AudioFormat.FLAC;
        };
    }
}
