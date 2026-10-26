package org.sona.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sona.auth.UserAuthentication;
import org.sona.controller.request.PlayRequest;
import org.sona.controller.response.RecentPlaysResponse;
import org.sona.db.PlayDao;
import org.sona.exception.TrackNotFoundException;
import org.sona.mappers.PlaylistResponseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own play history, which only they see.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class PlayController {

    private static final int RECENT_TRACKS = 20;

    private final PlayDao playDao;
    private final PlaylistResponseMapper mapper;

    /**
     * Sona records the time itself, as of this request.
     */
    @PostMapping(ApiRoutes.PLAYS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordPlay(final UserAuthentication authentication, @RequestBody @Valid final PlayRequest request) {
        playDao.insert(authentication.user().userId(), request.trackId())
                .orElseThrow(() -> new TrackNotFoundException(request.trackId()));
    }

    @GetMapping(ApiRoutes.RECENT_PLAYS)
    public RecentPlaysResponse recentPlays(final UserAuthentication authentication) {
        final var entries = playDao.loadRecentTracks(authentication.user().userId(), RECENT_TRACKS);
        final var tracks = entries.stream().map(mapper::toResponse).toList();
        return new RecentPlaysResponse(tracks);
    }
}
