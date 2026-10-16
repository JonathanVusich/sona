package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.ReleaseResponse;
import org.sona.db.ReleaseDetailsDao;
import org.sona.exception.ClientException;
import org.sona.mappers.ReleaseResponseMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class ReleaseController {

    private final ReleaseDetailsDao releaseDetailsDao;
    private final ReleaseResponseMapper mapper;

    @GetMapping(ApiRoutes.RELEASE)
    public ReleaseResponse release(@PathVariable final UUID releaseId) {
        return releaseDetailsDao.loadById(releaseId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ClientException(ErrorCode.RELEASE_NOT_FOUND));
    }
}
