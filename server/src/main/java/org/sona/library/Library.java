package org.sona.library;

import org.sona.model.Track;
import org.sona.model.tables.pojos.TrackToIngest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface Library {

    void storeIngestTrack(UUID ingestGroup, String filename, InputStream inputStream) throws IOException;

    InputStream readIngestTrack(TrackToIngest trackIngest) throws IOException;

    void importTrack(TrackToIngest trackIngest, Track targetTrack) throws IOException;
}
