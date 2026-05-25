package org.sona.metadata.resolver;

import org.sona.metadata.RawMetadata;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;

import java.io.IOException;
import java.util.Optional;

public interface MetadataResolver {

    Optional<Track> resolveTrack(TrackToIngest trackToIngest, RawMetadata metadata) throws IOException, InterruptedException;
}
