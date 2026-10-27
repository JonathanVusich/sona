package org.sona.db;

import java.util.List;

/**
 * A medium with its tracks in order.
 *
 * @param position on its release, from 1
 * @param format   like "CD"; null when MusicBrainz doesn't know
 */
public record MediumWithTracks(int position, String format, List<TrackWithArtist> tracks) {}
