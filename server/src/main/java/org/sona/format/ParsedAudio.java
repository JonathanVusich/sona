package org.sona.format;

import org.sona.metadata.RawMetadata;

import java.time.Duration;

/**
 * Everything a {@link Parser} extracts from an audio file: its length plus the embedded tags.
 */
public record ParsedAudio(
        Duration duration,
        RawMetadata metadata
) {
}
