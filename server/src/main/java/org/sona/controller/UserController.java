package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.auth.PasswordService;
import org.sona.auth.UserAuthentication;
import org.sona.auth.UserDirectory;
import org.sona.controller.request.ChangePasswordRequest;
import org.sona.controller.response.CurrentUserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

    private final UserDirectory userDirectory;
    private final PasswordService passwordService;

    @GetMapping(ApiRoutes.CURRENT_USER)
    public CurrentUserResponse me(final UserAuthentication authentication) {
        final var signedIn = authentication.user();
        final var user = userDirectory.resolve(signedIn);
        final var permissions = authentication.permissions().stream().sorted().toList();
        return new CurrentUserResponse(user.userId(), user.username(), signedIn.role(), signedIn.state(),
                permissions);
    }

    /**
     * Changes the password and ends every session the user has, so they log in again with the new one.
     */
    @PostMapping(ApiRoutes.CURRENT_USER_PASSWORD)
    public ResponseEntity<ProblemDetail> changePassword(final UserAuthentication authentication,
                                                        @RequestBody final ChangePasswordRequest request) {
        final var user = userDirectory.resolve(authentication.user());
        final var change = passwordService.change(user, request.currentPassword(), request.newPassword());
        return switch (change) {
            case CHANGED -> ResponseEntity.noContent().build();
            case INCORRECT_CURRENT_PASSWORD -> badRequest("The current password is incorrect");
            case INVALID_NEW_PASSWORD -> badRequest("The new password must not be empty or the same as the current one");
        };
    }

    private static ResponseEntity<ProblemDetail> badRequest(final String detail) {
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        return ResponseEntity.badRequest().body(problem);
    }
}
