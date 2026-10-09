package org.sona.controller.response;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Why Sona refused a request. Clients branch on the number, which never changes once released; the reason is for
 * people. Numbers are grouped by area: 1xx signing in and sessions, 2xx the user's own account.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    NOT_SIGNED_IN(100, HttpStatus.UNAUTHORIZED, "Sign in to continue."),
    INVALID_CREDENTIALS(101, HttpStatus.UNAUTHORIZED, "The username or password is incorrect."),
    INVALID_REFRESH_TOKEN(102, HttpStatus.UNAUTHORIZED, "Your session has ended. Sign in again."),
    PASSWORD_CHANGE_REQUIRED(103, HttpStatus.FORBIDDEN, "Change your password before continuing."),
    ACCESS_DENIED(104, HttpStatus.FORBIDDEN, "You don't have permission to do that."),

    INCORRECT_CURRENT_PASSWORD(200, HttpStatus.BAD_REQUEST, "The current password is incorrect."),
    INVALID_NEW_PASSWORD(201, HttpStatus.BAD_REQUEST,
            "The new password must not be empty or the same as the current one.");

    @JsonValue
    private final int code;
    private final HttpStatus status;
    private final String reason;
}
