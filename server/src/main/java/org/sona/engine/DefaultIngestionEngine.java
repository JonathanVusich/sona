package org.sona.engine;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.jooq.DSLContext;
import org.sona.library.Library;
import org.sona.metadata.resolver.MetadataResolver;
import org.sona.model.Tables;
import org.sona.model.Track;
import org.sona.model.enums.IngestState;
import org.sona.exception.InvalidFormatException;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.service.metadata.parsing.FlacParser;
import org.sona.service.metadata.parsing.Parser;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;
import static org.sona.model.tables.Track.TRACK;

@Service
@RequiredArgsConstructor
public final class DefaultIngestionEngine implements IngestionEngine {

    private static final Map<String, Parser> PARSERS = Stream.of(
            new FlacParser()
    ).flatMap(p -> p.format().getExtensions().stream().map(ext -> entry(ext, p)))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    private final Library library;
    private final MetadataResolver resolver;


    private final DSLContext dsl;

    @Override
    public void processTrack(final TrackToIngest trackToIngest) throws InvalidFormatException, IOException {
        final var extension = FilenameUtils.getExtension(trackToIngest.originalFileName());
        final var parser = PARSERS.get(extension);
        if (parser == null) {
            throw new InvalidFormatException("Extension %s is not supported!");
        }

        try (final var inputStream = library.readIngestTrack(trackToIngest)) {
            final var rawMetadata = parser.parse(inputStream);
            final var track = resolver.resolveTrack(rawMetadata)
                    .orElse(null);

            // No MusicBrainz track info present!
            if (track == null) {
                dsl.update(Tables.TRACK_TO_INGEST)
                        .set(Tables.TRACK_TO_INGEST.STATE, IngestState.INPUT_REQUIRED)
                        .where(Tables.TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngest.trackIngestId()))
                        .execute();

                // TODO: Refactor to support multiple metadata providers using plugins
                // TODO: Implement metadata searching
                return;
            }

            // TODO: Need to also recursively update artist + release information

            var record = dsl.newRecord(TRACK, track);
            record.changed(TRACK.TRACK_ID, false);

            final var updatedTrack = dsl.insertInto(TRACK)
                    .set(record)
                    .onConflict(TRACK.MUSICBRAINZ_TRACK_ID)
                    .doUpdate()
                    .set(TRACK.MUSICBRAINZ_TRACK_ID, TRACK.MUSICBRAINZ_TRACK_ID) // Keep the dummy update manually
                    .returning()
                    .fetchOneInto(Track.class);

            // TODO: We need to combine all of the info into a single metadata object that is passed to the library for importing.

            // We successfully imported the metadata.
            // The track should be copied into the library
            library.importTrack(trackToIngest, updatedTrack);

        }
    }

}


