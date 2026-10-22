package org.sona.metadata.resolver;

/**
 * One of the discs, or other media, a release is made of.
 *
 * @param position from 1
 * @param format   like "CD" or "Digital Media", or null when MusicBrainz doesn't know
 */
public record ResolvedMedium(int position, String format) {}
