package org.sona.engine;

import java.time.Duration;
import org.sona.fingerprint.Fingerprint;
import org.sona.format.Format;
import org.sona.metadata.RawMetadata;
import org.sona.model.tables.pojos.TrackToIngest;

/**
 * What an uploaded file holds: its tags, the duration of its decoded audio and the audio's fingerprint.
 */
public record ParsedTrack(
        TrackToIngest trackToIngest, Format format, RawMetadata metadata, Duration duration, Fingerprint fingerprint) {}
