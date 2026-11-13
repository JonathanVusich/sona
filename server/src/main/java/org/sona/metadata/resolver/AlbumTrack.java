package org.sona.metadata.resolver;

import dev.javax.sonaudio.fingerprint.Fingerprint;
import dev.javax.sonaudio.metadata.RawMetadata;
import java.time.Duration;
import org.sona.model.tables.pojos.TrackToIngest;

/**
 * A file of an album the uploader grouped together, as {@link MetadataResolver#resolveTrack} takes it.
 *
 * @param duration of the track's decoded audio
 */
public record AlbumTrack(
        TrackToIngest trackToIngest, RawMetadata metadata, Duration duration, Fingerprint fingerprint) {}
