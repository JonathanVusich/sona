package org.sona.metadata.resolver;

import java.util.UUID;

/**
 * A label a release came out on, matched to MusicBrainz.
 */
public record ResolvedLabel(UUID labelId, String name) {}
