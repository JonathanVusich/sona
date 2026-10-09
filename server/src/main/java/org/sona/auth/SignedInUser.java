package org.sona.auth;

import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;

import java.util.UUID;

/**
 * Who made a request, as their access token says. Nothing here is looked up in the database.
 *
 * @param userId the user's Sona ID, the token's subject
 */
public record SignedInUser(UUID userId, String username, UserRole role, AccountState state) {
}
