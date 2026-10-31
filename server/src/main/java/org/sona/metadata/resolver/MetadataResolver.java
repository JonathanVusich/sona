package org.sona.metadata.resolver;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.sona.fingerprint.Fingerprint;
import org.sona.metadata.RawMetadata;
import org.sona.model.tables.pojos.TrackToIngest;

public interface MetadataResolver {

    /**
     * @param duration of the track's decoded audio, which the file's header may misstate
     * @param fingerprint of the track's audio: a close match settles the recording whatever the tags say, and a
     * looser one helps choose between those the tags find
     */
    TrackResolution resolveTrack(
            TrackToIngest trackToIngest, RawMetadata metadata, Duration duration, Fingerprint fingerprint)
            throws IOException, InterruptedException;

    /**
     * Resolves the files of an album together, so they land on one release: the one that most of their recordings are
     * on, rather than whichever edition suits each file alone. A file that isn't on that release needs review.
     *
     * @return a resolution for each track, in the order given
     */
    List<TrackResolution> resolveAlbum(List<AlbumTrack> tracks) throws IOException, InterruptedException;

    /**
     * @return the release a resolved track is on, with its release group and artists
     */
    ResolvedRelease resolveRelease(UUID releaseId);

    /**
     * @return the artist, with the details search results leave out
     */
    ResolvedArtist resolveArtist(UUID artistId);
}
