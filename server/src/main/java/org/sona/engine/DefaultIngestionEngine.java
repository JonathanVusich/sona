package org.sona.engine;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.sona.library.Library;
import org.sona.model.enums.IngestState;
import org.sona.model.exception.InvalidFormatException;
import org.sona.model.tables.daos.TrackDao;
import org.sona.model.tables.daos.TrackIngestDao;
import org.sona.model.tables.pojos.TrackIngest;
import org.sona.model.tables.records.TrackIngestRecord;
import org.sona.service.metadata.parsing.FlacParser;
import org.sona.service.metadata.parsing.Parser;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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
    private final TrackDao trackDao;
    private final TrackIngestDao ingestDao;

    @Override
    public void processTrack(final TrackIngest trackToIngest) throws InvalidFormatException, IOException {
        final var extension = FilenameUtils.getExtension(trackToIngest.originalFileName());
        final var parser = PARSERS.get(extension);
        if (parser == null) {
            throw new InvalidFormatException("Extension %s is not supported!");
        }
        try (final var inputStream = Files.newInputStream(trackToIngest., StandardOpenOption.READ)) {
            final var trackMetadata = parser.parse(inputStream);

            // TODO: Refactor to support multiple metadata providers using plugins
            if (!trackMetadata.hasMbMetadata()) {

                final var recordToUpdate = new TrackIngestRecord(trackToIngest);
                recordToUpdate.setState(IngestState.INPUT_REQUIRED);

                ingestDao.ctx().executeUpdate(recordToUpdate);

                ingestDao.update(recordToUpdate);
            }
        }
    }

}


