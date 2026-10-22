package org.sona.auth;

import static org.sona.utils.IDGenerator.uuidv7;

import lombok.RequiredArgsConstructor;
import org.sona.db.PasswordDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.PasswordState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Password;
import org.sona.model.tables.pojos.Users;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates local users whose password is their username.
 */
@RequiredArgsConstructor
public final class TestUsers {

    private final UserDao userDao;
    private final PasswordDao passwordDao;
    private final PasswordEncoder encoder;

    public Users createUser(final String username, final UserRole role) {
        return createUser(username, role, AccountState.ACTIVE, PasswordState.VALID);
    }

    public Users createUser(final String username, final UserRole role, final AccountState state) {
        return createUser(username, role, state, PasswordState.VALID);
    }

    public Users createUser(final String username, final UserRole role, final PasswordState passwordState) {
        return createUser(username, role, AccountState.ACTIVE, passwordState);
    }

    private Users createUser(
            final String username, final UserRole role, final AccountState state, final PasswordState passwordState) {
        final var user = new Users(uuidv7(), UserType.LOCAL, username, role, state);
        userDao.insert(user);
        passwordDao.upsert(new Password(user.userId(), encoder.encode(username), passwordState));
        return user;
    }
}
