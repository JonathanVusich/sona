package org.sona.engine;

import dev.javax.sonaudio.exception.InvalidFormatException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.sona.metadata.resolver.AlbumResolution;
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
     * @param parsedTracks the album's files that could be read: only they are matched
     */
    AlbumResolution resolveAlbum(List<ParsedTrack> parsedTracks);

    /**
     * @return the track, stored with the rows it references, whose file is still to be moved into the library
     */
    Track storeTrack(ParsedTrack parsed, ResolvedTrack resolved);

    void requireInput(TrackToIngest trackToIngest, List<TrackCandidate> candidates);

    /**
     * Stores the releases the album might be, for the uploader to choose between.
     *
     * @param files the album's files that could be read, which wait for the choice
     */
    void requireAlbumInput(UUID ingestGroupId, List<TrackToIngest> files, AlbumResolution.NeedsReview review);

    /**
     * Moves the track's file into the library, which completes its ingest.
     */
    void moveTrack(TrackToIngest trackToIngest, Track track) throws IOException;

    /**
     * Records the ingest's state and tells its uploader.
     */
    void markState(TrackToIngest trackToIngest, IngestState state);
}
