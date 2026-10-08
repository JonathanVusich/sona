package org.sona.engine;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.sona.exception.InvalidFormatException;
import org.sona.format.Format;
import org.sona.format.Parser;
import org.sona.format.flac.FlacParser;
import org.sona.library.Library;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.resolver.MetadataResolver;
import org.sona.metadata.resolver.ResolvedTrack;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.store.IngestStore;
import org.sona.store.TrackStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
public final class DefaultIngestionEngine implements IngestionEngine {

    private static final Map<String, Parser> PARSERS = Stream.of(
            new FlacParser()
    ).flatMap(p -> p.format().getExtensions().stream().map(ext -> entry(ext, p)))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    private final Library library;
    private final MetadataResolver resolver;
    private final IngestStore ingestStore;
    private final TrackStore trackStore;

    @Override
    public void processTrack(final TrackToIngest trackToIngest) throws InvalidFormatException, IOException, InterruptedException {
        switch (trackToIngest.state()) {
            case PENDING, INPUT_REQUIRED -> ingestTrack(trackToIngest);
            case MOVE_FAILED -> retryMove(trackToIngest);
            case COMPLETED -> throw new IllegalStateException(
                    "Track %s has already been imported".formatted(trackToIngest.trackIngestId()));
        }
    }

    private void ingestTrack(final TrackToIngest trackToIngest) throws InvalidFormatException, IOException, InterruptedException {
        final var parser = parserFor(trackToIngest);
        final var metadata = parseIngestTrack(trackToIngest, parser);
        final var resolved = resolver.resolveTrack(trackToIngest, metadata)
                .orElse(null);

        // No MusicBrainz track info present!
        if (resolved == null) {
            ingestStore.markState(trackToIngest.trackIngestId(), IngestState.INPUT_REQUIRED);

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
        final var track = trackStore.storeTrack(trackToIngest, resolved, duration, format);
        moveIntoLibrary(trackToIngest, track);
    }

    private void retryMove(final TrackToIngest trackToIngest) throws IOException {
        // The metadata was stored before the move failed, so only the move is retried.
        final var track = trackStore.loadByIngest(trackToIngest.trackIngestId());
        moveIntoLibrary(trackToIngest, track);
    }

    private void moveIntoLibrary(final TrackToIngest trackToIngest, final Track track) throws IOException {
        try {
            library.importTrack(trackToIngest, track.path());
        } catch (IOException e) {
            ingestStore.markState(trackToIngest.trackIngestId(), IngestState.MOVE_FAILED);
            throw e;
        }
        ingestStore.markState(trackToIngest.trackIngestId(), IngestState.COMPLETED);
    }
}
