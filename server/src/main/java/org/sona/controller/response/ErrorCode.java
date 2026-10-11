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
    PASSWORD_CHANGE_REQUIRED(103, HttpStatus.FORBIDDEN, "Change your password before continuing."),
    ACCESS_DENIED(104, HttpStatus.FORBIDDEN, "You don't have permission to do that.");

    @JsonValue
    private final int code;
    private final HttpStatus status;
    private final String reason;
}
