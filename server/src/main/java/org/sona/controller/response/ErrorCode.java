package org.sona.controller.response;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Why Sona refused a request. Clients branch on the number, which never changes once released; the reason is for
 * people. Numbers are grouped by area: 1xx signing in and sessions, 2xx the user's own account, 3xx the
 * library, 4xx requests Sona can't read.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    NOT_SIGNED_IN(100, HttpStatus.UNAUTHORIZED, "Sign in to continue."),
    INVALID_CREDENTIALS(101, HttpStatus.UNAUTHORIZED, "The username or password is incorrect."),
    INVALID_REFRESH_TOKEN(102, HttpStatus.UNAUTHORIZED, "Your session has ended. Sign in again."),
    PASSWORD_CHANGE_REQUIRED(103, HttpStatus.FORBIDDEN, "Change your password before continuing."),
    ACCESS_DENIED(104, HttpStatus.FORBIDDEN, "You don't have permission to do that."),
    ACCOUNT_DISABLED(105, HttpStatus.FORBIDDEN, "Your account is disabled."),
    SIGN_IN_FAILED(106, HttpStatus.UNAUTHORIZED, "Signing in through your identity provider failed. Try again."),
    INVALID_REGISTRATION_TOKEN(107, HttpStatus.UNAUTHORIZED, "Your sign-up has expired. Sign in again."),
    SIGN_IN_REQUIRED(108, HttpStatus.UNAUTHORIZED, "Sign in to continue."),

    INCORRECT_CURRENT_PASSWORD(200, HttpStatus.BAD_REQUEST, "The current password is incorrect."),
    INVALID_NEW_PASSWORD(201, HttpStatus.BAD_REQUEST, "The new password must not be the same as the current one."),
    PASSWORD_MANAGED_BY_PROVIDER(202, HttpStatus.BAD_REQUEST,
            "You sign in through your identity provider, so change your password there."),
    USERNAME_TAKEN(203, HttpStatus.CONFLICT, "That username is taken."),
    ALREADY_REGISTERED(205, HttpStatus.CONFLICT, "You already have an account."),

    RELEASE_NOT_FOUND(300, HttpStatus.NOT_FOUND, "This release isn't in the library."),
    RELEASE_GROUP_NOT_FOUND(301, HttpStatus.NOT_FOUND, "This release group isn't in the library."),
    ARTIST_NOT_FOUND(302, HttpStatus.NOT_FOUND, "This artist isn't in the library."),
    UNSUPPORTED_FORMAT(303, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Sona can't ingest files in this format yet."),

    INVALID_PARAMETER(400, HttpStatus.BAD_REQUEST, "The request has a parameter or field that is missing or invalid."),
    UNSUPPORTED_CONTENT_TYPE(401, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The request body isn't in a type this endpoint takes.");

    @JsonValue
    private final int code;
    private final HttpStatus status;
    private final String reason;
}
