package org.sona.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ReleaseGroupPageResponse;
import org.sona.controller.response.ReleaseGroupResponse;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.ReleaseGroupSummary;
import org.sona.exception.ReleaseGroupNotFoundException;
import org.sona.mappers.ReleaseGroupResponseMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class ReleaseGroupController {

    private static final int MAX_LIMIT = 100;

    private final ReleaseGroupDao releaseGroupDao;
    private final ReleaseGroupResponseMapper mapper;

    /**
     * @param after the {@code next} of the page before, or nothing for the first page
     * @param limit how many release groups a page holds, up to {@value MAX_LIMIT}
     */
    @GetMapping(ApiRoutes.RELEASE_GROUPS)
    public ReleaseGroupPageResponse releaseGroups(
            @RequestParam(required = false) final UUID after,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_LIMIT) final int limit) {
        // One more than the page holds tells whether there's a page after it.
        if (after == null) {
            return pageResponse(releaseGroupDao.loadFirstPage(limit + 1), limit);
        }
        return pageResponse(releaseGroupDao.loadNextPage(after, limit + 1), limit);
    }

    @GetMapping(ApiRoutes.RELEASE_GROUP)
    public ReleaseGroupResponse releaseGroup(@PathVariable final UUID releaseGroupId) {
        return releaseGroupDao
                .loadDetails(releaseGroupId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ReleaseGroupNotFoundException(releaseGroupId));
    }

    /**
     * @param loaded up to one more release group than the page holds
     */
    private ReleaseGroupPageResponse pageResponse(final List<ReleaseGroupSummary> loaded, final int limit) {
        final var page = loaded.stream().limit(limit).toList();
        final var releaseGroups = page.stream().map(mapper::toResponse).toList();
        if (loaded.size() == page.size()) {
            // The response marks the last page with a null next.
            return new ReleaseGroupPageResponse(releaseGroups, null);
        }
        return new ReleaseGroupPageResponse(
                releaseGroups, page.getLast().releaseGroup().releaseGroupId());
    }
}
