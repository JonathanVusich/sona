package org.sona.controller;

import org.sona.controller.response.ErrorResponse;
import org.sona.exception.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> refused(final ApiException exception) {
        final var code = exception.code();
        return ResponseEntity.status(code.getStatus()).body(new ErrorResponse(code));
    }
}
