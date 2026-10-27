package org.sona.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sona.auth.UserAuthentication;
import org.sona.controller.request.CurrentEntryRequest;
import org.sona.controller.request.QueueModesRequest;
import org.sona.controller.request.QueuePositionRequest;
import org.sona.controller.request.ReplaceQueueRequest;
import org.sona.controller.response.QueueResponse;
import org.sona.db.QueueDao;
import org.sona.exception.QueueEntryNotFoundException;
import org.sona.mappers.QueueResponseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own play queue, which they can pick up from any of their devices.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class QueueController {

    private final QueueDao queueDao;
    private final QueueResponseMapper mapper;

    /**
     * An empty queue, at version 0, for a user who has never changed theirs.
     */
    @GetMapping(ApiRoutes.QUEUE)
    public QueueResponse queue(final UserAuthentication authentication) {
        return mapper.toResponse(queueDao.loadDetails(authentication.user().userId()));
    }

    /**
     * What playing a list of tracks does: they become the whole queue, the one at {@code startIndex} is current, and
     * every entry gets an ID.
     */
    @PutMapping(ApiRoutes.QUEUE)
    public QueueResponse replaceQueue(
            final UserAuthentication authentication, @RequestBody @Valid final ReplaceQueueRequest request) {
        final var queue = queueDao.replace(authentication.user().userId(), request.trackIds(), request.startIndex());
        return mapper.toResponse(queue);
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
}
