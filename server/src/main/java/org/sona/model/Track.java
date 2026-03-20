package org.sona.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

public record Track(
    UUID trackId,
    String name,
    Duration duration,
    Format format,
    UUID artistId,
    String artistName,
    UUID releaseId,
    String releaseName
) {}
