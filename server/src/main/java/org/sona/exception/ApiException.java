package org.sona.exception;

import org.sona.controller.response.ErrorCode;

/**
 * Refuses the request being handled with a client error, which {@code ApiExceptionHandler} turns into an
 * {@code ErrorResponse}.
 */
public final class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(final ErrorCode code) {
        super(code.getReason());
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
