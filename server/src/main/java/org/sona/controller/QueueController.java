package org.sona.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.auth.UserAuthentication;
import org.sona.controller.request.CurrentEntryRequest;
import org.sona.controller.request.QueueModesRequest;
import org.sona.controller.request.QueuePositionRequest;
import org.sona.controller.request.QueueStartRequest;
import org.sona.controller.request.ReplaceQueueRequest;
import org.sona.controller.response.QueueEntryPageResponse;
import org.sona.controller.response.QueueResponse;
import org.sona.db.QueueDao;
import org.sona.db.QueueEntry;
import org.sona.exception.QueueEntryNotFoundException;
import org.sona.mappers.QueueResponseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own play queue, which they can pick up from any of their devices. A queue may be as long as a
 * playlist, so it is read as its state and then a page of entries at a time, and long sources fill it from the
 * server's side.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class QueueController {

    private static final int MAX_LIMIT = 100;

    private final QueueDao queueDao;
    private final QueueResponseMapper mapper;

    /**
     * The queue without its entries: an empty queue, at version 0, for a user who has never changed theirs.
     */
    @GetMapping(ApiRoutes.QUEUE)
    public QueueResponse queue(final UserAuthentication authentication) {
        return mapper.toResponse(queueDao.loadDetails(authentication.user().userId()));
    }

    /**
     * The entries that play after the cursor, in play order.
     *
     * @param after the {@code next} of the page before, or the current entry's {@code playOrder} to start after it; 0,
     *              the default, is the first page
     * @param limit how many entries a page holds, up to {@value MAX_LIMIT}
     */
    @GetMapping(ApiRoutes.QUEUE_ENTRIES)
    public QueueEntryPageResponse entries(
            final UserAuthentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) final int after,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_LIMIT) final int limit) {
        // One more than the page holds tells whether there's a page after it.
        final var loaded = queueDao.loadEntriesAfter(authentication.user().userId(), after, limit + 1);
        final var page = loaded.stream().limit(limit).toList();
        if (loaded.size() == page.size()) {
            return pageResponse(page, null);
        }
        return pageResponse(page, page.getLast().playOrder());
    }

    /**
     * The entries that play before the cursor, going back from it, a page of them in play order.
     *
     * @param before the {@code playOrder} of the entry after the page, or the {@code next} of the page after
     * @param limit  how many entries a page holds, up to {@value MAX_LIMIT}
     */
    @GetMapping(ApiRoutes.QUEUE_EARLIER_ENTRIES)
    public QueueEntryPageResponse earlierEntries(
            final UserAuthentication authentication,
            @RequestParam @Min(1) final int before,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_LIMIT) final int limit) {
        final var loaded = queueDao.loadEntriesBefore(authentication.user().userId(), before, limit + 1);
        final var page = loaded.stream().limit(limit).toList().reversed();
        if (loaded.size() == page.size()) {
            return pageResponse(page, null);
        }
        return pageResponse(page, page.getFirst().playOrder());
    }

    /**
     * What playing a list of tracks does: they become the whole queue, the one at {@code startIndex} is current, and
     * every entry gets an ID.
     */
    @PutMapping(ApiRoutes.QUEUE)
    public QueueResponse replaceQueue(
            final UserAuthentication authentication, @RequestBody @Valid final ReplaceQueueRequest request) {
        final var userId = authentication.user().userId();
        return mapper.toResponse(queueDao.replaceWithTracks(userId, request.trackIds(), request.startIndex()));
    }

    /**
     * What playing a playlist does, as {@link #replaceQueue} does for a list of tracks, however long the playlist is.
     */
    @PutMapping(ApiRoutes.QUEUE_PLAYLIST)
    public QueueResponse replaceQueueWithPlaylist(
            final UserAuthentication authentication,
            @PathVariable final UUID playlistId,
            @RequestBody @Valid final QueueStartRequest request) {
        final var userId = authentication.user().userId();
        return mapper.toResponse(queueDao.replaceWithPlaylist(userId, playlistId, request.startIndex()));
    }

    /**
     * What playing a release does, as {@link #replaceQueue} does for a list of tracks.
     */
    @PutMapping(ApiRoutes.QUEUE_RELEASE)
    public QueueResponse replaceQueueWithRelease(
            final UserAuthentication authentication,
            @PathVariable final UUID releaseId,
            @RequestBody @Valid final QueueStartRequest request) {
        final var userId = authentication.user().userId();
        return mapper.toResponse(queueDao.replaceWithRelease(userId, releaseId, request.startIndex()));
    }

    /**
     * Empties the queue. Shuffle and repeat stay as the user set them.
     */
    @DeleteMapping(ApiRoutes.QUEUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearQueue(final UserAuthentication authentication) {
        queueDao.delete(authentication.user().userId());
    }

    /**
     * Moves the player to the entry, at its start.
     */
    @PutMapping(ApiRoutes.QUEUE_CURRENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateCurrentEntry(
            final UserAuthentication authentication, @RequestBody @Valid final CurrentEntryRequest request) {
        queueDao.updateCurrentEntry(authentication.user().userId(), request.entryId())
                .orElseThrow(() -> new QueueEntryNotFoundException(request.entryId()));
    }

    /**
     * Where the player is in the current entry, which the player reports as it plays. A report for an entry that
     * isn't the current one is out of date, and does nothing.
     */
    @PutMapping(ApiRoutes.QUEUE_POSITION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePosition(
            final UserAuthentication authentication, @RequestBody @Valid final QueuePositionRequest request) {
        queueDao.updatePositionSeconds(authentication.user().userId(), request.entryId(), request.seconds());
    }

    @PutMapping(ApiRoutes.QUEUE_MODES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateModes(
            final UserAuthentication authentication, @RequestBody @Valid final QueueModesRequest request) {
        queueDao.updateModes(authentication.user().userId(), request.shuffle(), request.repeat());
    }

    private QueueEntryPageResponse pageResponse(final List<QueueEntry> page, final Integer next) {
        final var entries = page.stream().map(mapper::toResponse).toList();
        return new QueueEntryPageResponse(entries, next);
    }
}
