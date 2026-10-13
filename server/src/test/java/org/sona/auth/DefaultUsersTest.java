package org.sona.auth;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.db.PasswordDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.PasswordState;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.model.Tables.USERS;

/**
 * The accounts Sona creates for itself: admin/admin on a fresh install, and admin/admin plus user/user in the dev
 * profile.
 */
@IntegrationTest
class DefaultUsersTest {

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordDao passwordDao;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    DSLContext dsl;

    @Test
    void aFreshInstallGetsAnAdminWhoMustChangeTheirPassword() {
        // The application already ran this at startup, against the same empty database.
        dsl.truncate(USERS).cascade().execute();

        new InitialAdminRunner(userDao, passwordDao, encoder).run(new DefaultApplicationArguments());

        final var admin = userDao.loadByUsername("admin").orElseThrow();
        assertThat(admin.role()).isEqualTo(UserRole.ADMIN);
        final var password = passwordDao.loadByUserId(admin.userId()).orElseThrow();
        assertThat(password.state()).isEqualTo(PasswordState.CHANGE_REQUIRED);
        assertThat(encoder.matches("admin", password.passwordHash())).isTrue();
    }

    @Test
    void noAdminIsCreatedOnceUsersExist() {
        dsl.truncate(USERS).cascade().execute();
        new TestUsers(userDao, passwordDao, encoder).createUser("alice", UserRole.USER);

        new InitialAdminRunner(userDao, passwordDao, encoder).run(new DefaultApplicationArguments());

        assertThat(userDao.loadByUsername("admin")).isEmpty();
    }

    @Test
    void theDevProfileGetsAnActiveAdminAndUser() {
        dsl.truncate(USERS).cascade().execute();

        new DevUserRunner(userDao, passwordDao, encoder).run(new DefaultApplicationArguments());

        final var admin = userDao.loadByUsername("admin").orElseThrow();
        final var user = userDao.loadByUsername("user").orElseThrow();
        assertThat(admin.role()).isEqualTo(UserRole.ADMIN);
        assertThat(user.role()).isEqualTo(UserRole.USER);
        assertThat(admin.state()).isEqualTo(AccountState.ACTIVE);
        final var password = passwordDao.loadByUserId(user.userId()).orElseThrow();
        assertThat(encoder.matches("user", password.passwordHash())).isTrue();
    }

    @Test
    void theDevProfileResetsTheDevUsers() {
        dsl.truncate(USERS).cascade().execute();
        final var existing = new TestUsers(userDao, passwordDao, encoder)
                .createUser("user", UserRole.ADMIN, AccountState.DISABLED);

        new DevUserRunner(userDao, passwordDao, encoder).run(new DefaultApplicationArguments());

        final var user = userDao.loadByUsername("user").orElseThrow();
        assertThat(user.userId()).isEqualTo(existing.userId());
        assertThat(user.role()).isEqualTo(UserRole.USER);
        assertThat(user.state()).isEqualTo(AccountState.ACTIVE);
    }
}
