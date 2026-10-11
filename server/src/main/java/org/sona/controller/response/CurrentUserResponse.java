package org.sona.controller.response;

import org.sona.auth.Permission;
import org.sona.auth.SessionState;
import org.sona.model.enums.UserRole;

import java.util.List;
import java.util.UUID;

/**
 * @param permissions what the user may do now, which is nothing while they must change their password
 */
public record CurrentUserResponse(
        UUID userId,
        String username,
        UserRole role,
        SessionState state,
        List<Permission> permissions
) {
}
