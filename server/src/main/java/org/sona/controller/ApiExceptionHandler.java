package org.sona.controller;

import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.ErrorResponse;
import org.sona.exception.ClientException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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

    /**
     * A request body whose content type the endpoint doesn't take.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedContentType() {
        return handleClientException(new ClientException(ErrorCode.UNSUPPORTED_CONTENT_TYPE));
    }

    /**
     * A query parameter the endpoint requires that the request leaves out.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter() {
        return handleClientException(new ClientException(ErrorCode.INVALID_PARAMETER));
    }

    /**
     * A parameter that breaks the endpoint's Jakarta validation constraints ({@code @Min}, {@code @Max}, ...), or a
     * {@code @Valid} request body whose fields break theirs.
     */
    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleConstraintViolation() {
        return handleClientException(new ClientException(ErrorCode.INVALID_PARAMETER));
    }
}
