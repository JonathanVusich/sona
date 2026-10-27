package org.sona.db;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A release, read in one query with its artist, its labels by name, and its media in order, each with its tracks in
 * order and each track with its own artist.
 *
 * @param date    null when unknown
 * @param country null when unknown
 */
public record ReleaseDetails(
        UUID releaseId,
        String name,
        LocalDate date,
        String country,
        int trackCount,
        UUID releaseGroupId,
        UUID artistId,
        String artistName,
        List<LabelSummary> labels,
        List<MediumWithTracks> media) {}
