package org.sona.db;

import java.util.List;
import org.sona.model.tables.pojos.Medium;

/**
 * A medium with its tracks in order.
 */
public record MediumWithTracks(Medium medium, List<TrackWithArtist> tracks) {}
