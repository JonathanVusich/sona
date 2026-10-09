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
 * Creates admin/admin and user/user for local development, resetting them on every start.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevUsers implements ApplicationRunner {

    private final LocalUserDao localUserDao;
    private final PasswordEncoder encoder;

    @Override
    public void run(final ApplicationArguments args) {
        localUserDao.upsert(user("admin", UserRole.ADMIN));
        localUserDao.upsert(user("user", UserRole.USER));
    }

    private LocalUser user(final String name, final UserRole role) {
        return new LocalUser(uuidv7(), name, encoder.encode(name), role, AccountState.ACTIVE);
    }
}
