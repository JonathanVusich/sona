package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.ReleaseGroupPageResponse;
import org.sona.controller.response.ReleaseGroupResponse;
import org.sona.db.ReleaseGroupDetailsDao;
import org.sona.db.ReleaseGroupSummary;
import org.sona.db.ReleaseGroupSummaryDao;
import org.sona.exception.ClientException;
import org.sona.mappers.ReleaseGroupResponseMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class ReleaseGroupController {

    private static final int MAX_LIMIT = 100;

    private final ReleaseGroupSummaryDao summaryDao;
    private final ReleaseGroupDetailsDao detailsDao;
    private final ReleaseGroupResponseMapper mapper;

    /**
     * @param after the {@code next} of the page before, or nothing for the first page
     * @param limit how many release groups a page holds, up to {@value MAX_LIMIT}
     */
    @GetMapping(ApiRoutes.RELEASE_GROUPS)
    public ReleaseGroupPageResponse releaseGroups(@RequestParam(required = false) final UUID after,
                                                  @RequestParam(defaultValue = "50") final int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ClientException(ErrorCode.INVALID_PARAMETER);
        }
        // One more than the page holds tells whether there's a page after it.
        final var loaded = summaryDao.loadPage(after, limit + 1);
        final var page = loaded.subList(0, Math.min(limit, loaded.size()));
        final var releaseGroups = page.stream().map(mapper::toResponse).toList();
        return new ReleaseGroupPageResponse(releaseGroups, nextPage(loaded, page));
    }

    @GetMapping(ApiRoutes.RELEASE_GROUP)
    public ReleaseGroupResponse releaseGroup(@PathVariable final UUID releaseGroupId) {
        return detailsDao.loadById(releaseGroupId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ClientException(ErrorCode.RELEASE_GROUP_NOT_FOUND));
    }

    /**
     * @return the last release group on the page, or null when nothing comes after it
     */
    private static UUID nextPage(final List<ReleaseGroupSummary> loaded, final List<ReleaseGroupSummary> page) {
        if (loaded.size() == page.size()) {
            return null;
        }
        return page.getLast().releaseGroup().releaseGroupId();
    }
}
