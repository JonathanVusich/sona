package org.sona.library;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.UUID;
import org.sona.exception.InvalidFormatException;
import org.sona.format.Format;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.core.io.Resource;

public interface Library {

    /**
     * @param ingestGroup the group the uploader created for the file
     * @param filename    the file's name where it was uploaded from, whose extension says its format
     * @return the stored ingest, waiting to be processed
     * @throws InvalidFormatException if the file isn't in a format Sona supports, in which case nothing is stored
     * @throws org.sona.exception.FileTooLargeException if the file is larger than {@code library.maxUploadSize}, in
     *     which case nothing is stored
     */
    TrackToIngest storeIngestTrack(IngestGroup ingestGroup, String filename, InputStream inputStream)
            throws InvalidFormatException, IOException;

    SeekableByteChannel readIngestTrack(TrackToIngest trackIngest) throws IOException;

    /**
     * @return where a track lives in the library, relative to the media folder and '/'-separated
     */
    String libraryPath(ReleaseGroup releaseGroup, Release release, UUID trackId, String trackName, Format format);

    /**
     * Moves an ingested file to {@code libraryPath} (as returned by {@link #libraryPath}).
     */
    void importTrack(TrackToIngest trackIngest, String libraryPath) throws IOException;

    InputStream readTrack(Track track) throws IOException;

    /**
     * @return the track's file in the library, for serving to clients
     */
    Resource trackFile(Track track);
}
