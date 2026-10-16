package org.sona.controller.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the release page shows.
 *
 * @param date       null when unknown; MusicBrainz may only know the year or month, and the parts it doesn't know are
 *                   the first: "2002" is 2002-01-01
 * @param country    ISO 3166-1 code, or a MusicBrainz code like "XW" for worldwide; null when unknown
 * @param trackCount how many of the release's tracks are in the library
 * @param labels     by name
 * @param media      in order
 */
public record ReleaseResponse(
        UUID releaseId,
        String name,
        LocalDate date,
        String country,
        int trackCount,
        UUID releaseGroupId,
        ArtistResponse artist,
        List<LabelResponse> labels,
        List<MediumResponse> media
) {
}
