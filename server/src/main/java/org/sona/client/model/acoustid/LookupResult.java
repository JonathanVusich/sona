package org.sona.client.model.acoustid;

import java.util.List;
import java.util.UUID;

/**
 * @param id the AcoustID track's ID
 * @param score how closely the track's fingerprints match the one looked up, from 0 to 1
 * @param recordings the MusicBrainz recordings the track is linked to; null when it's linked to none
 */
public record LookupResult(UUID id, double score, List<RecordingId> recordings) {}
