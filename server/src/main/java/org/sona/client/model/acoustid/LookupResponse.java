package org.sona.client.model.acoustid;

import java.util.List;

/**
 * AcoustID's answer to a fingerprint lookup: the AcoustID tracks whose fingerprints are like it, if any.
 */
public record LookupResponse(List<LookupResult> results) {}
