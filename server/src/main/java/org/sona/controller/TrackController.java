package org.sona.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.auth.StreamTokenService;
import org.sona.auth.UserAuthentication;
import org.sona.controller.response.StreamTokenResponse;
import org.sona.db.TrackDao;
import org.sona.exception.TrackNotFoundException;
import org.sona.library.Library;
import org.sona.model.enums.AudioFormat;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TrackController {

    private static final MediaType FLAC = MediaType.parseMediaType("audio/flac");
    private static final MediaType MP3 = MediaType.parseMediaType("audio/mpeg");

    private final StreamTokenService streamTokenService;
    private final TrackDao trackDao;
    private final Library library;

    /**
     * Doesn't check that the track is in the library, as streaming it does.
     */
    @GetMapping(ApiRoutes.TRACK_STREAM_TOKEN)
    @PreAuthorize("hasAuthority('library:read')")
    public StreamTokenResponse streamToken(final UserAuthentication authentication, @PathVariable final UUID trackId) {
        final var userId = authentication.user().userId();
        return new StreamTokenResponse(streamTokenService.issueToken(userId, trackId));
    }

    /**
     * Authorized by the stream token alone (see {@code SecurityConfig}), which only users who may read the library
     * are given. Spring answers range requests for the resource with only the parts asked for.
     */
    @GetMapping(ApiRoutes.TRACK_STREAM)
    public ResponseEntity<Resource> stream(@PathVariable final UUID trackId) {
        final var track = trackDao.loadById(trackId).orElseThrow(() -> new TrackNotFoundException(trackId));
        final var file = library.trackFile(track);
        return ResponseEntity.ok().contentType(mediaType(track.format())).body(file);
    }

    private static MediaType mediaType(final AudioFormat format) {
        return switch (format) {
            case FLAC -> FLAC;
            case MP3 -> MP3;
        };
    }
}
