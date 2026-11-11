package org.sona.db;

import java.util.List;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;

/**
 * A stored release, with its media in order.
 */
public record ReleaseWithMedia(Release release, List<Medium> media) {}
