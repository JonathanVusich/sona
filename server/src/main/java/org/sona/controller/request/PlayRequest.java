package org.sona.controller.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param trackId the track the user has played, which the player reports once it has played half of it, or four minutes
 */
public record PlayRequest(@NotNull UUID trackId) {}
