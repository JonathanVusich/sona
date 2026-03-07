package org.sona.library;

import org.sona.metadata.MusicBrainzMetadata;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackIngest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface Library {

    void storeIngestTrack(UUID ingestGroup, String filename, InputStream inputStream) throws IOException;

    InputStream readIngestTrack(TrackIngest trackIngest) throws IOException;

    void importTrack(TrackIngest trackIngest, Track targetTrack) throws IOException;
}
