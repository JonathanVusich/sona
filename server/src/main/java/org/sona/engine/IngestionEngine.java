package org.sona.engine;

import java.io.IOException;
import java.util.List;
import org.sona.exception.InvalidFormatException;
import org.sona.metadata.resolver.ResolvedTrack;
import org.sona.metadata.resolver.TrackCandidate;
import org.sona.metadata.resolver.TrackResolution;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;

/**
 * The steps of ingesting an uploaded file: parse it, resolve it (alone, or with the rest of its album), then store
 * the track with what it references and move its file into the library, or store its candidates. Nothing here
 * catches a failure: each step throws it to the {@link IngestWorker}, which records what it means.
 */
public interface IngestionEngine {

    ParsedTrack parseTrack(TrackToIngest trackToIngest) throws InvalidFormatException, IOException;

    TrackResolution resolveTrack(ParsedTrack parsed);

    /**
     * Resolves the files of an album together, so they are imported as one release.
     *
     * @return a resolution for each track, in the order given
     */
    List<TrackResolution> resolveAlbum(List<ParsedTrack> parsedTracks);

    /**
     * @return the track, stored with the rows it references, whose file is still to be moved into the library
     */
    Track storeTrack(ParsedTrack parsed, ResolvedTrack resolved);

    void requireInput(TrackToIngest trackToIngest, List<TrackCandidate> candidates);

    /**
     * Moves the track's file into the library, which completes its ingest.
     */
    void moveTrack(TrackToIngest trackToIngest, Track track) throws IOException;

    /**
     * Records the ingest's state and tells its uploader.
     */
    void markState(TrackToIngest trackToIngest, IngestState state);
}
