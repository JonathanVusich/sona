package org.sona.controller.response;

/**
 * @param added how many of the tracks were added; tracks already on the playlist, or not in the library, aren't
 */
public record AddedTracksResponse(int added) {}
