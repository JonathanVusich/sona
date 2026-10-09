package org.sona.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sona.model.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

/**
 * What a user may do. Endpoints check these with {@code @PreAuthorize("hasAuthority('library:read')")}.
 */
@Getter
@RequiredArgsConstructor
public enum Permission implements GrantedAuthority {
    LIBRARY_READ("library:read"),
    LIBRARY_WRITE("library:write"),
    LIBRARY_UPLOAD("library:upload"),
    USERS_MANAGE("users:manage");

    private final String authority;

    /**
     * @return the permissions every user with the role has
     */
    public static Set<Permission> granted(final UserRole role) {
        return switch (role) {
            case ADMIN -> Set.of(values());
            case USER -> Set.of(LIBRARY_READ);
        };
    }
}
