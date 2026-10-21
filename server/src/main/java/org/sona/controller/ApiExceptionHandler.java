package org.sona.controller;

import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.ErrorResponse;
import org.sona.exception.AlreadyRegisteredException;
import org.sona.exception.ArtistNotFoundException;
import org.sona.exception.IncorrectPasswordException;
import org.sona.exception.IngestGroupNotFoundException;
import org.sona.exception.InvalidCredentialsException;
import org.sona.exception.InvalidFormatException;
import org.sona.exception.InvalidRefreshTokenException;
import org.sona.exception.InvalidRegistrationTokenException;
import org.sona.exception.PasswordManagedByProviderException;
import org.sona.exception.ReleaseGroupNotFoundException;
import org.sona.exception.ReleaseNotFoundException;
import org.sona.exception.SonaException;
import org.sona.exception.UnchangedPasswordException;
import org.sona.exception.UsernameTakenException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Answers every exception a request can end in with an {@link ErrorResponse}. Controllers and services throw domain
 * exceptions ({@link SonaException}, {@link InvalidFormatException}), never error codes, and this is the one place
 * that decides how each is answered.
 */
@RestControllerAdvice
public final class ApiExceptionHandler {

    @ExceptionHandler(SonaException.class)
    public ResponseEntity<ErrorResponse> handleSonaException(final SonaException exception) {
        final var code = errorCode(exception);
        return errorResponse(code);
    }

    /**
     * An upload in a format Sona has no parser for.
     */
    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFormat() {
        return errorResponse(ErrorCode.UNSUPPORTED_FORMAT);
    }

    /**
     * A path or query parameter that isn't the type the endpoint takes, such as an ID that isn't a UUID.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch() {
        return errorResponse(ErrorCode.INVALID_PARAMETER);
    }

    /**
     * A JSON request body that can't be read, such as one with a value no enum has.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody() {
        return errorResponse(ErrorCode.INVALID_PARAMETER);
    }

    /**
     * A request body whose content type the endpoint doesn't take.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedContentType() {
        return errorResponse(ErrorCode.UNSUPPORTED_CONTENT_TYPE);
    }

    /**
     * A query parameter the endpoint requires that the request leaves out.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter() {
        return errorResponse(ErrorCode.INVALID_PARAMETER);
    }

    /**
     * A refresh or logout without the refresh cookie, the only cookie an endpoint requires: the browser has no session
     * to refresh or end.
     */
    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ErrorResponse> handleMissingCookie() {
        return errorResponse(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    /**
     * A parameter that breaks the endpoint's Jakarta validation constraints ({@code @Min}, {@code @Max}, ...), or a
     * {@code @Valid} request body whose fields break theirs.
     */
    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleConstraintViolation() {
        return errorResponse(ErrorCode.INVALID_PARAMETER);
    }

    private static ErrorCode errorCode(final SonaException exception) {
        return switch (exception) {
            case ReleaseNotFoundException _ -> ErrorCode.RELEASE_NOT_FOUND;
            case ReleaseGroupNotFoundException _ -> ErrorCode.RELEASE_GROUP_NOT_FOUND;
            case ArtistNotFoundException _ -> ErrorCode.ARTIST_NOT_FOUND;
            case IngestGroupNotFoundException _ -> ErrorCode.INGEST_GROUP_NOT_FOUND;
            case InvalidCredentialsException _ -> ErrorCode.INVALID_CREDENTIALS;
            case InvalidRefreshTokenException _ -> ErrorCode.INVALID_REFRESH_TOKEN;
            case IncorrectPasswordException _ -> ErrorCode.INCORRECT_CURRENT_PASSWORD;
            case UnchangedPasswordException _ -> ErrorCode.INVALID_NEW_PASSWORD;
            case PasswordManagedByProviderException _ -> ErrorCode.PASSWORD_MANAGED_BY_PROVIDER;
            case InvalidRegistrationTokenException _ -> ErrorCode.INVALID_REGISTRATION_TOKEN;
            case AlreadyRegisteredException _ -> ErrorCode.ALREADY_REGISTERED;
            case UsernameTakenException _ -> ErrorCode.USERNAME_TAKEN;
        };
    }

    private static ResponseEntity<ErrorResponse> errorResponse(final ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(new ErrorResponse(code));
    }
}
