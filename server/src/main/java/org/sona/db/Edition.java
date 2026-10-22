package org.sona.db;

import java.util.List;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;

/**
 * One of a release group's releases, as its page lists them: with its labels by name and its media in order.
 */
public record Edition(Release release, List<Label> labels, List<Medium> media) {}
