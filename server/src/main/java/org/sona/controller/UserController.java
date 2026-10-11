package org.sona.controller;

import org.sona.auth.UserAuthentication;
import org.sona.controller.response.CurrentUserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own account.
 */
@RestController
public final class UserController {

    /**
     * Answered from the access token alone, which carries everything here.
     */
    @GetMapping(ApiRoutes.CURRENT_USER)
    public CurrentUserResponse me(final UserAuthentication authentication) {
        final var user = authentication.user();
        final var permissions = authentication.permissions().stream().sorted().toList();
        return new CurrentUserResponse(user.userId(), user.username(), user.role(), user.state(), permissions);
    }
}
