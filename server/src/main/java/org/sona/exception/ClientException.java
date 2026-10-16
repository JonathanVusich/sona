package org.sona.exception;

import org.sona.controller.response.ErrorCode;

import java.io.Serial;

/**
 * Refuses the request being handled with a client error, which {@code ApiExceptionHandler} turns into an
 * {@code ErrorResponse}.
 */
public final class ClientException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public ClientException(final ErrorCode code) {
        super(code.getReason());
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
