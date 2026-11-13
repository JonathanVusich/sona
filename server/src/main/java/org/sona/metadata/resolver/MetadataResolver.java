package org.sona.metadata.resolver;

import dev.javax.sonaudio.fingerprint.Fingerprint;
import dev.javax.sonaudio.metadata.RawMetadata;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.sona.model.tables.pojos.TrackToIngest;

public interface MetadataResolver {

    /**
     * @param duration of the track's decoded audio, which the file's header may misstate
     * @param fingerprint of the track's audio: a close match settles the recording whatever the tags say, and a
     * looser one helps choose between those the tags find
     */
    TrackResolution resolveTrack(
            TrackToIngest trackToIngest, RawMetadata metadata, Duration duration, Fingerprint fingerprint);

    /**
     * Resolves the files of an album together, onto one release: one that all of it, or one of its media, has as many
     * tracks as the album has files. The releases most of their recordings are on are tried first, and the first that
     * places every file on a track as long as its audio is the match. Otherwise the releases that have as many tracks
     * need review.
     *
     * @param tracks the album's files that could be read: only they are matched
     */
    AlbumResolution resolveAlbum(List<AlbumTrack> tracks);

    /**
     * @return the release a resolved track is on, with its release group and artists
     */
    ResolvedRelease resolveRelease(UUID releaseId);

    /**
     * @return the artist, with the details search results leave out
     */
    ResolvedArtist resolveArtist(UUID artistId);
}
