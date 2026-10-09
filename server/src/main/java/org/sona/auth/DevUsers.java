package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Users;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Creates admin/admin and user/user for local development, resetting them on every start.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevUsers implements ApplicationRunner {

    private final UserDao userDao;
    private final PasswordEncoder encoder;

    @Override
    public void run(final ApplicationArguments args) {
        userDao.upsert(user("admin", UserRole.ADMIN));
        userDao.upsert(user("user", UserRole.USER));
    }

    private Users user(final String name, final UserRole role) {
        return new Users(uuidv7(), name, encoder.encode(name), role, AccountState.ACTIVE, null, null);
    }
}
