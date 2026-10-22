package org.sona.controller.response;

import java.util.List;
import java.util.UUID;
import org.sona.auth.Permission;
import org.sona.auth.SessionState;
import org.sona.model.enums.UserRole;

/**
 * @param permissions   what the user may do now, which is nothing while they must change their password
 * @param maxUploadSize the largest file Sona accepts as an upload, in bytes, so uploaders can leave out larger ones
 *                      rather than send them only to be refused
 */
public record CurrentUserResponse(
        UUID userId,
        String username,
        UserRole role,
        SessionState state,
        List<Permission> permissions,
        long maxUploadSize) {}
