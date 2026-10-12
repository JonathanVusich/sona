package org.sona.controller;

import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.ErrorResponse;
import org.sona.exception.ClientException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public final class ApiExceptionHandler {

    @ExceptionHandler(ClientException.class)
    public ResponseEntity<ErrorResponse> handleClientException(final ClientException exception) {
        final var code = exception.code();
        return ResponseEntity.status(code.getStatus()).body(new ErrorResponse(code));
    }

    /**
     * A path or query parameter that isn't the type the endpoint takes, such as an ID that isn't a UUID.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch() {
        return handleClientException(new ClientException(ErrorCode.INVALID_PARAMETER));
    }
}
