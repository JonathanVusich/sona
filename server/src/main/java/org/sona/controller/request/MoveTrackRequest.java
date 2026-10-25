package org.sona.controller.request;

import jakarta.validation.constraints.Min;

/**
 * @param position where the track goes on the playlist, from 1; past the end puts it last
 */
public record MoveTrackRequest(@Min(1) int position) {}
