package org.sona.auth;

import java.util.Set;
import java.util.UUID;
import org.sona.model.enums.UserRole;

/**
 * Who made a request, as their access token says. Nothing here is looked up in the database.
 *
 * @param userId the user's Sona ID, the token's subject
 */
public record SignedInUser(UUID userId, String username, UserRole role, SessionState state) {

    /**
     * @return what the user may do in this session
     */
    public Set<Permission> permissions() {
        return switch (state) {
            case ACTIVE -> Permission.grantedPermissions(role);
            // Until they change their password, the user may only do that.
            case PASSWORD_CHANGE_REQUIRED -> Set.of();
        };
    }
}
