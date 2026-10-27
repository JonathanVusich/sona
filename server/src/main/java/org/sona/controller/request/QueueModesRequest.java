package org.sona.controller.request;

import jakarta.validation.constraints.NotNull;
import org.sona.model.enums.RepeatMode;
import org.sona.model.enums.ShuffleMode;

public record QueueModesRequest(
        @NotNull ShuffleMode shuffle, @NotNull RepeatMode repeat) {}
