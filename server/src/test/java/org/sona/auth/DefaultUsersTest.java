package org.sona.auth;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
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
    PasswordEncoder encoder;

    @Autowired
    DSLContext dsl;

    @Test
    void aFreshInstallGetsAnAdminWhoMustChangeTheirPassword() {
        // The application already ran this at startup, against the same empty database.
        dsl.truncate(USERS).cascade().execute();

        new InitialAdmin(userDao, encoder).run(new DefaultApplicationArguments());

        final var admin = userDao.findByUsername("admin").orElseThrow();
        assertThat(admin.role()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.state()).isEqualTo(AccountState.PASSWORD_CHANGE_REQUIRED);
        assertThat(encoder.matches("admin", admin.passwordHash())).isTrue();
    }

    @Test
    void noAdminIsCreatedOnceUsersExist() {
        dsl.truncate(USERS).cascade().execute();
        new TestUsers(userDao, encoder).create("alice", UserRole.USER);

        new InitialAdmin(userDao, encoder).run(new DefaultApplicationArguments());

        assertThat(userDao.findByUsername("admin")).isEmpty();
    }

    @Test
    void theDevProfileGetsAnActiveAdminAndUser() {
        dsl.truncate(USERS).cascade().execute();

        new DevUsers(userDao, encoder).run(new DefaultApplicationArguments());

        final var admin = userDao.findByUsername("admin").orElseThrow();
        final var user = userDao.findByUsername("user").orElseThrow();
        assertThat(admin.role()).isEqualTo(UserRole.ADMIN);
        assertThat(user.role()).isEqualTo(UserRole.USER);
        assertThat(admin.state()).isEqualTo(AccountState.ACTIVE);
        assertThat(encoder.matches("user", user.passwordHash())).isTrue();
    }

    @Test
    void theDevProfileResetsTheDevUsers() {
        dsl.truncate(USERS).cascade().execute();
        final var existing = new TestUsers(userDao, encoder).create("user", UserRole.ADMIN, AccountState.DISABLED);

        new DevUsers(userDao, encoder).run(new DefaultApplicationArguments());

        final var user = userDao.findByUsername("user").orElseThrow();
        assertThat(user.userId()).isEqualTo(existing.userId());
        assertThat(user.role()).isEqualTo(UserRole.USER);
        assertThat(user.state()).isEqualTo(AccountState.ACTIVE);
    }
}
