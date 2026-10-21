package org.sona.controller.response;

import java.util.List;
import java.util.UUID;

/**
 * One page of the release groups, by name.
 *
 * @param next what to pass as {@code after} for the next page, or null when this is the last
 */
public record ReleaseGroupPageResponse(List<ReleaseGroupSummaryResponse> releaseGroups, UUID next) {}
