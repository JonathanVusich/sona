package org.sona.controller.response;

/**
 * The body of every client error Sona sends.
 *
 * @param code   what went wrong, written as its number
 * @param reason what went wrong, for people
 */
public record ErrorResponse(ErrorCode code, String reason) {

    public ErrorResponse(final ErrorCode code) {
        this(code, code.getReason());
    }
}
