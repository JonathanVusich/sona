package org.sona.library;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.UUID;
import org.sona.exception.InvalidFormatException;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.core.io.Resource;

public interface Library {

    /**
     * @param filename the file's name where it was uploaded from, whose extension says its format
     * @throws InvalidFormatException if the file isn't in a format Sona supports
     */
    void checkIngestFormat(String filename) throws InvalidFormatException;

    /**
     * Stores the bytes of a file the uploader listed when it created the group. The file may be sent again until it
     * has been stored, such as after a connection broke.
     *
     * @param ingestGroupId the group the uploader created for the file
     * @param trackIngestId the ID Sona gave the file when the group was created
     * @return the ingest, now uploaded and waiting to be processed
     * @throws org.sona.exception.IngestTrackNotFoundException if the group has no such file
     * @throws org.sona.exception.IngestAlreadyUploadedException if the file has been stored already
     * @throws org.sona.exception.FileTooLargeException if the file is larger than {@code library.maxUploadSize}, in
     *     which case nothing is stored
     */
    TrackToIngest storeIngestTrack(UUID ingestGroupId, UUID trackIngestId, InputStream inputStream) throws IOException;

    SeekableByteChannel readIngestTrack(TrackToIngest trackIngest) throws IOException;

    /**
     * @param trackIngest the track's ingest, whose filename's extension the file keeps in the library
     * @return where a track lives in the library, relative to the media folder and '/'-separated
     */
    String libraryPath(
            ReleaseGroup releaseGroup, Release release, UUID trackId, String trackName, TrackToIngest trackIngest);

    /**
     * Moves an ingested file to {@code libraryPath} (as returned by {@link #libraryPath}).
     */
    void importTrack(TrackToIngest trackIngest, String libraryPath) throws IOException;

    /**
     * @return the track's file in the library, which opens a new stream each time it's read, as range requests do
     */
    Resource trackFile(Track track);
}
