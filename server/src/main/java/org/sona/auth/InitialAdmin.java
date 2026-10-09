package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.LocalUserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.LocalUser;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Creates admin/admin on a fresh install, so someone can log in. The password is public, so it must be changed
 * before the account can do anything else.
 */
@Component
@Profile("!dev")
@RequiredArgsConstructor
public class InitialAdmin implements ApplicationRunner {

    private final LocalUserDao localUserDao;
    private final PasswordEncoder encoder;

    @Override
    public void run(final ApplicationArguments args) {
        if (localUserDao.isEmpty()) {
            final var admin = new LocalUser(uuidv7(), "admin", encoder.encode("admin"), UserRole.ADMIN,
                    AccountState.PASSWORD_CHANGE_REQUIRED);
            localUserDao.insert(admin);
        }
    }
}
