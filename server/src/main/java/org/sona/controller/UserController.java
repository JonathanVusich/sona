package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.auth.PasswordService;
import org.sona.auth.UserAuthentication;
import org.sona.controller.request.ChangePasswordRequest;
import org.sona.controller.response.CurrentUserResponse;
import org.sona.controller.response.ErrorCode;
import org.sona.exception.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own account.
 */
@RestController
@RequiredArgsConstructor
public final class UserController {

    private final PasswordService passwordService;

    /**
     * Answered from the access token alone, which carries everything here.
     */
    @GetMapping(ApiRoutes.CURRENT_USER)
    public CurrentUserResponse me(final UserAuthentication authentication) {
        final var user = authentication.user();
        final var permissions = authentication.permissions().stream().sorted().toList();
        return new CurrentUserResponse(user.userId(), user.username(), user.role(), user.state(), permissions);
    }

    /**
     * Changes the password and ends every session the user has, so they log in again with the new one.
     */
    @PostMapping(ApiRoutes.CURRENT_USER_PASSWORD)
    public ResponseEntity<Void> changePassword(final UserAuthentication authentication,
                                               @RequestBody final ChangePasswordRequest request) {
        final var change = passwordService.change(authentication.user(), request.currentPassword(),
                request.newPassword());
        return switch (change) {
            case CHANGED -> ResponseEntity.noContent().build();
            case INCORRECT_CURRENT_PASSWORD -> throw new ApiException(ErrorCode.INCORRECT_CURRENT_PASSWORD);
            case INVALID_NEW_PASSWORD -> throw new ApiException(ErrorCode.INVALID_NEW_PASSWORD);
        };
    }
}
