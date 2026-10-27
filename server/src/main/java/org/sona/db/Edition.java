package org.sona.db;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One of a release group's releases, as its page lists them: with its labels by name and each of its media's formats
 * in order.
 *
 * @param date    null when unknown
 * @param country null when unknown
 * @param formats null for a medium whose format MusicBrainz doesn't know
 */
public record Edition(
        UUID releaseId,
        String name,
        LocalDate date,
        String country,
        int trackCount,
        List<LabelSummary> labels,
        List<String> formats) {}
