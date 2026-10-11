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
public class DevUserRunner implements ApplicationRunner {

    private final UserDao userDao;
    private final PasswordDao passwordDao;
    private final PasswordEncoder encoder;

    @Override
    public void run(final ApplicationArguments args) {
        reset("admin", UserRole.ADMIN);
        reset("user", UserRole.USER);
    }

    private void reset(final String name, final UserRole role) {
        final var user = userDao.upsert(new Users(uuidv7(), UserType.LOCAL, name, role, AccountState.ACTIVE));
        passwordDao.upsert(new Password(user.userId(), encoder.encode(name), PasswordState.VALID));
    }
}
