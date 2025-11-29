package org.sona.library;

import org.sona.model.tables.pojos.TrackIngest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface Library {

    TrackIngest storeIngestTrack(UUID ingestGroup, String filename, InputStream inputStream) throws IOException;

    InputStream readIngestTrack(UUID ingestGroup, UUID trackId) throws IOException;
}
