package org.sona.metadata.resolver;

/**
 * A recording the file might be, on the release that suits its tags best.
 *
 * @param score how well it matches, from MusicBrainz's search score and adjusted for how the file's tags and audio
 *              agree; the more the better, and over 100 for a recording the audio also matched
 */
public record TrackCandidate(ResolvedTrack track, String releaseTitle, int score) {}
