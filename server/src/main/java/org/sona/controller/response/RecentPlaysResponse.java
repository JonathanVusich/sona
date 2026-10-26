package org.sona.controller.response;

import java.util.List;

/**
 * @param tracks the tracks the user played last, latest first; one that was played again is listed again
 */
public record RecentPlaysResponse(List<PlaylistTrackResponse> tracks) {}
