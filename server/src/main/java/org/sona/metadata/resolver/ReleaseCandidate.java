package org.sona.metadata.resolver;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A release an album's files might be: all of it, or one of its media, has as many tracks as the album has files.
 *
 * @param artist      who the release is credited to; credits leave out their country
 * @param date        MusicBrainz may only know the year or month, and the parts it doesn't know are the first; null
 *                    when it doesn't know any
 * @param country     ISO 3166-1 code, or a MusicBrainz code like "XW" for worldwide; null when MusicBrainz doesn't
 *                    know it
 * @param placedCount how many of the files Sona could place on its tracks
 */
public record ReleaseCandidate(
        UUID releaseId,
        String title,
        ResolvedArtist artist,
        LocalDate date,
        String country,
        ReleaseScope scope,
        int placedCount) {}
