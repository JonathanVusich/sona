package org.sona.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ReleaseResponse;
import org.sona.db.ReleaseDao;
import org.sona.exception.ReleaseNotFoundException;
import org.sona.mappers.ReleaseResponseMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class ReleaseController {

    private final ReleaseDao releaseDao;
    private final ReleaseResponseMapper mapper;

    @GetMapping(ApiRoutes.RELEASE)
    public ReleaseResponse release(@PathVariable final UUID releaseId) {
        return releaseDao
                .loadDetails(releaseId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ReleaseNotFoundException(releaseId));
    }
}
