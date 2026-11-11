package org.sona.engine;

import dev.javax.sonaudio.fingerprint.Fingerprint;
import dev.javax.sonaudio.format.Format;
import dev.javax.sonaudio.metadata.RawMetadata;
import java.time.Duration;
import org.sona.model.tables.pojos.TrackToIngest;

/**
 * What an uploaded file holds: its tags, the duration of its decoded audio and the audio's fingerprint.
 */
public record ParsedTrack(
        TrackToIngest trackToIngest, Format format, RawMetadata metadata, Duration duration, Fingerprint fingerprint) {}
