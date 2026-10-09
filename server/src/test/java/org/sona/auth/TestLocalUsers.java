package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.LocalUserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.LocalUser;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Creates local users whose password is their username.
 */
@RequiredArgsConstructor
public final class TestLocalUsers {

    private final LocalUserDao localUserDao;
    private final PasswordEncoder encoder;

    public LocalUser create(final String username, final UserRole role, final AccountState state) {
        final var user = new LocalUser(uuidv7(), username, encoder.encode(username), role, state);
        localUserDao.insert(user);
        return user;
    }

    public LocalUser create(final String username, final UserRole role) {
        return create(username, role, AccountState.ACTIVE);
    }
}
