package org.sona.controller.response;

import java.util.List;

/**
 * @param position on its release, from 1
 * @param format   like "CD" or "Digital Media"; null when MusicBrainz doesn't know
 * @param tracks   in order
 */
public record MediumResponse(int position, String format, List<TrackResponse> tracks) {}
