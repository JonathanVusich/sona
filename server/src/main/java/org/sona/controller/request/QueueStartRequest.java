package org.sona.controller.request;

import jakarta.validation.constraints.Min;

/**
 * @param startIndex which of the source's tracks plays first, from 0
 */
public record QueueStartRequest(@Min(0) int startIndex) {}
