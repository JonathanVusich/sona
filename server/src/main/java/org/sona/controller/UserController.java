package org.sona.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sona.auth.PasswordChecker;
import org.sona.auth.UserAuthentication;
import org.sona.config.properties.LibraryProperties;
import org.sona.controller.request.ChangePasswordRequest;
import org.sona.controller.response.CurrentUserResponse;
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

    private final PasswordChecker passwordChecker;
    private final LibraryProperties libraryProperties;

    /**
     * Answered from the access token alone, which carries everything here but Sona's own settings.
     */
    @GetMapping(ApiRoutes.CURRENT_USER)
    public CurrentUserResponse currentUser(final UserAuthentication authentication) {
        final var user = authentication.user();
        final var permissions = authentication.permissions().stream().sorted().toList();
        final var maxUploadSize = libraryProperties.maxUploadSize().toBytes();
        return new CurrentUserResponse(
                user.userId(), user.username(), user.role(), user.state(), permissions, maxUploadSize);
    }

    /**
     * Changes the password and ends every session the user has, so they log in again with the new one.
     */
    @PostMapping(ApiRoutes.CURRENT_USER_PASSWORD)
    public ResponseEntity<Void> changePassword(
            final UserAuthentication authentication, @RequestBody @Valid final ChangePasswordRequest request) {
        passwordChecker.changePassword(authentication.user(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
