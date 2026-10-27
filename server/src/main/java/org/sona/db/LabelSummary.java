package org.sona.db;

import java.util.UUID;

/**
 * A label a release came out on, as the pages that list a release's labels show it.
 */
public record LabelSummary(UUID labelId, String name) {}
