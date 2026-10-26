package org.sona.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.auth.UserAuthentication;
import org.sona.controller.request.PlayRequest;
import org.sona.controller.response.PlayPageResponse;
import org.sona.db.PlayDao;
import org.sona.db.PlayedTrack;
import org.sona.exception.TrackNotFoundException;
import org.sona.mappers.PlayResponseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own play history, which only they see.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class PlayController {

    private static final int MAX_LIMIT = 100;

    private final PlayDao playDao;
    private final PlayResponseMapper mapper;

    /**
     * Sona records the time itself, as of this request.
     */
    @PostMapping(ApiRoutes.PLAYS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordPlay(final UserAuthentication authentication, @RequestBody @Valid final PlayRequest request) {
        playDao.insert(authentication.user().userId(), request.trackId())
                .orElseThrow(() -> new TrackNotFoundException(request.trackId()));
    }

    /**
     * The user's plays, latest first, a track played again listed again.
     *
     * @param after the {@code next} of the page before, or nothing for the first page
     * @param limit how many plays a page holds, up to {@value MAX_LIMIT}
     */
    @GetMapping(ApiRoutes.PLAYS)
    public PlayPageResponse plays(
            final UserAuthentication authentication,
            @RequestParam(required = false) final UUID after,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_LIMIT) final int limit) {
        final var userId = authentication.user().userId();
        // One more than the page holds tells whether there's a page after it.
        if (after == null) {
            return pageResponse(playDao.loadFirstPage(userId, limit + 1), limit);
        }
        return pageResponse(playDao.loadNextPage(userId, after, limit + 1), limit);
    }

    /**
     * @param loaded up to one more play than the page holds
     */
    private PlayPageResponse pageResponse(final List<PlayedTrack> loaded, final int limit) {
        final var page = loaded.stream().limit(limit).toList();
        final var plays = page.stream().map(mapper::toResponse).toList();
        if (loaded.size() == page.size()) {
            // The response marks the last page with a null next.
            return new PlayPageResponse(plays, null);
        }
        return new PlayPageResponse(plays, page.getLast().play().playId());
    }
}
