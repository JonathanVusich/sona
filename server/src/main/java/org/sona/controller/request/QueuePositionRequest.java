package org.sona.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param entryId the entry the player is on; ignored if the queue has moved on to another since
 * @param seconds how far into it the player is
 */
public record QueuePositionRequest(
        @NotNull UUID entryId, @Min(0) int seconds) {}
