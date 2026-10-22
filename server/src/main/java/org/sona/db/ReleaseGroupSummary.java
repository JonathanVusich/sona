package org.sona.db;

import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;

/**
 * A release group with its artist, as the list of albums shows it.
 */
public record ReleaseGroupSummary(ReleaseGroup releaseGroup, Artist artist) {}
