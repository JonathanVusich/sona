package org.sona.controller.response;

import java.util.List;
import java.util.UUID;

/**
 * The release that stands for its group, whose tracks the release group page shows.
 *
 * @param media in order, each with its tracks
 */
public record DefaultReleaseResponse(UUID releaseId, List<MediumResponse> media) {}
