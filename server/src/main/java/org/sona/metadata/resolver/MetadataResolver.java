package org.sona.metadata.resolver;

import org.sona.metadata.RawMetadata;
import org.sona.model.tables.pojos.Track;

import java.util.Optional;

public interface MetadataResolver {

    Optional<Track> resolveTrack(RawMetadata metadata);
}
