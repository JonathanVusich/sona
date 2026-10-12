package org.sona.metadata.resolver;

import org.sona.metadata.RawMetadata;
import org.sona.model.tables.pojos.TrackToIngest;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public interface MetadataResolver {

    Optional<ResolvedTrack> resolveTrack(TrackToIngest trackToIngest, RawMetadata metadata) throws IOException, InterruptedException;

    /**
     * @return the release a resolved track is on, with its release group and artists
     */
    ResolvedRelease resolveRelease(UUID releaseId);

    /**
     * @return the artist, with the details search results leave out
     */
    ResolvedArtist resolveArtist(UUID artistId);
}
