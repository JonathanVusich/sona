package org.sona.metadata.resolver;

import java.io.IOException;
import java.util.UUID;
import org.sona.fingerprint.Fingerprint;
import org.sona.metadata.RawMetadata;
import org.sona.model.tables.pojos.TrackToIngest;

public interface MetadataResolver {

    /**
     * @param fingerprint of the track's audio: a close match settles the recording whatever the tags say, and a
     * looser one helps choose between those the tags find
     */
    TrackResolution resolveTrack(TrackToIngest trackToIngest, RawMetadata metadata, Fingerprint fingerprint)
            throws IOException, InterruptedException;

    /**
     * @return the release a resolved track is on, with its release group and artists
     */
    ResolvedRelease resolveRelease(UUID releaseId);

    /**
     * @return the artist, with the details search results leave out
     */
    ResolvedArtist resolveArtist(UUID artistId);
}
