package org.sona.controller.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param entryId the entry of the user's queue the player is now on
 */
public record CurrentEntryRequest(@NotNull UUID entryId) {}
