package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Users;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Creates local users whose password is their username.
 */
@RequiredArgsConstructor
public final class TestUsers {

    private final UserDao userDao;
    private final PasswordEncoder encoder;

    public Users create(final String username, final UserRole role, final AccountState state) {
        final var user = new Users(uuidv7(), username, encoder.encode(username), role, state, null, null);
        userDao.insert(user);
        return user;
    }

    public Users create(final String username, final UserRole role) {
        return create(username, role, AccountState.ACTIVE);
    }
}
