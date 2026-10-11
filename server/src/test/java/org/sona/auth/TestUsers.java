package org.sona.auth;

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

import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Creates local users whose password is their username.
 */
@RequiredArgsConstructor
public final class TestUsers {

    private final UserDao userDao;
    private final PasswordDao passwordDao;
    private final PasswordEncoder encoder;

    public Users create(final String username, final UserRole role) {
        return create(username, role, AccountState.ACTIVE, PasswordState.VALID);
    }

    public Users create(final String username, final UserRole role, final AccountState state) {
        return create(username, role, state, PasswordState.VALID);
    }

    public Users create(final String username, final UserRole role, final PasswordState passwordState) {
        return create(username, role, AccountState.ACTIVE, passwordState);
    }

    private Users create(final String username, final UserRole role, final AccountState state,
                         final PasswordState passwordState) {
        final var user = new Users(uuidv7(), UserType.LOCAL, username, role, state);
        userDao.insert(user);
        passwordDao.upsert(new Password(user.userId(), encoder.encode(username), passwordState));
        return user;
    }
}
