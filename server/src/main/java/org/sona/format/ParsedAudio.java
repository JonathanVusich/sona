package org.sona.format;

import org.sona.metadata.RawMetadata;

/**
 * An audio file whose metadata a {@link Parser} has read.
 */
public interface ParsedAudio {

    RawMetadata metadata();
}
