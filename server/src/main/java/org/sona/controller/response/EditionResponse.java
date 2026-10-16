package org.sona.controller.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One of a release group's releases, as its page lists them.
 *
 * @param date       null when unknown, as on {@link ReleaseResponse}
 * @param country    null when unknown, as on {@link ReleaseResponse}
 * @param trackCount how many of the release's tracks are in the library
 * @param labels     by name
 * @param formats    each medium's format in order, like "CD"; null for a medium whose format MusicBrainz doesn't know
 */
public record EditionResponse(
        UUID releaseId,
        String name,
        LocalDate date,
        String country,
        int trackCount,
        List<LabelResponse> labels,
        List<String> formats
) {
}
