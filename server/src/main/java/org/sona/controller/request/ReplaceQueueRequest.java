package org.sona.controller.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * @param trackIds   the tracks the queue is to hold, in order; each must be in the library
 * @param startIndex which of the tracks plays first, from 0
 */
public record ReplaceQueueRequest(
        @NotEmpty @Size(max = QueueLimits.MAX_TRACKS) List<@NotNull UUID> trackIds,
        @Min(0) int startIndex) {

    @JsonIgnore
    @AssertTrue
    public boolean isStartInRange() {
        return trackIds == null || startIndex < trackIds.size();
    }
}
