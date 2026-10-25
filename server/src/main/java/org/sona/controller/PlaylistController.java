package org.sona.controller;

import static org.sona.utils.IDGenerator.uuidv7;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.auth.UserAuthentication;
import org.sona.controller.request.AddTracksRequest;
import org.sona.controller.request.CreatePlaylistRequest;
import org.sona.controller.request.MoveTrackRequest;
import org.sona.controller.request.PlaylistDetailsRequest;
import org.sona.controller.response.AddedTracksResponse;
import org.sona.controller.response.CreatedPlaylistResponse;
import org.sona.controller.response.PlaylistResponse;
import org.sona.controller.response.PlaylistsResponse;
import org.sona.db.PlaylistDao;
import org.sona.exception.PlaylistNotFoundException;
import org.sona.mappers.PlaylistResponseMapper;
import org.sona.model.tables.pojos.Playlist;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A user's own playlists, which only they see. Another user's playlist is answered as not found, so its ID tells
 * nothing.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class PlaylistController {

    private final PlaylistDao playlistDao;
    private final PlaylistResponseMapper mapper;

    @GetMapping(ApiRoutes.PLAYLISTS)
    public PlaylistsResponse playlists(final UserAuthentication authentication) {
        final var userId = authentication.user().userId();
        final var playlists = playlistDao.loadSummaries(userId).stream()
                .map(mapper::toResponse)
                .toList();
        return new PlaylistsResponse(playlists);
    }

    @GetMapping(ApiRoutes.PLAYLIST)
    public PlaylistResponse playlist(final UserAuthentication authentication, @PathVariable final UUID playlistId) {
        return playlistDao
                .loadDetails(authentication.user().userId(), playlistId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
    }

    @PostMapping(ApiRoutes.PLAYLISTS)
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedPlaylistResponse createPlaylist(
            final UserAuthentication authentication, @RequestBody @Valid final CreatePlaylistRequest request) {
        final var userId = authentication.user().userId();
        final var playlist = new Playlist(uuidv7(), userId, request.name(), request.description(), 0);
        playlistDao.insert(playlist);
        playlistDao.insertTracks(userId, playlist.playlistId(), request.trackIds());
        return new CreatedPlaylistResponse(playlist.playlistId());
    }

    @PutMapping(ApiRoutes.PLAYLIST)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePlaylist(
            final UserAuthentication authentication,
            @PathVariable final UUID playlistId,
            @RequestBody @Valid final PlaylistDetailsRequest request) {
        playlistDao
                .updateDetails(authentication.user().userId(), playlistId, request.name(), request.description())
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
    }

    /**
     * The playlist's tracks stay in the library.
     */
    @DeleteMapping(ApiRoutes.PLAYLIST)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlaylist(final UserAuthentication authentication, @PathVariable final UUID playlistId) {
        playlistDao
                .delete(authentication.user().userId(), playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
    }

    /**
     * The tracks after it move up. Removing a track that isn't on the playlist does nothing, so a removal sent twice
     * does no harm.
     */
    @DeleteMapping(ApiRoutes.PLAYLIST_TRACK)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTrack(
            final UserAuthentication authentication,
            @PathVariable final UUID playlistId,
            @PathVariable final UUID trackId) {
        playlistDao
                .deleteTrack(authentication.user().userId(), playlistId, trackId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
    }

    /**
     * The tracks it passes move along by one. Moving a track that isn't on the playlist does nothing.
     */
    @PutMapping(ApiRoutes.PLAYLIST_TRACK_POSITION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moveTrack(
            final UserAuthentication authentication,
            @PathVariable final UUID playlistId,
            @PathVariable final UUID trackId,
            @RequestBody @Valid final MoveTrackRequest request) {
        playlistDao
                .updateTrackPosition(authentication.user().userId(), playlistId, trackId, request.position())
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
    }

    @PostMapping(ApiRoutes.PLAYLIST_TRACKS)
    public AddedTracksResponse addTracks(
            final UserAuthentication authentication,
            @PathVariable final UUID playlistId,
            @RequestBody @Valid final AddTracksRequest request) {
        return playlistDao
                .insertTracks(authentication.user().userId(), playlistId, request.trackIds())
                .map(AddedTracksResponse::new)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
    }
}
