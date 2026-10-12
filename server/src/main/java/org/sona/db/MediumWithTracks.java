package org.sona.db;

import org.sona.model.tables.pojos.Medium;

import java.util.List;

/**
 * A medium with its tracks in order.
 */
public record MediumWithTracks(Medium medium, List<TrackWithArtist> tracks) {
}
