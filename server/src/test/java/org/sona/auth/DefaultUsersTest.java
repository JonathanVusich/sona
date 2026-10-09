package org.sona.auth;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.db.LocalUserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.model.Tables.LOCAL_USER;

/**
 * The accounts Sona creates for itself: admin/admin on a fresh install, and admin/admin plus user/user in the dev
 * profile.
 */
@IntegrationTest
class DefaultUsersTest {

    @Autowired
    LocalUserDao localUserDao;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    DSLContext dsl;

    @Test
    void aFreshInstallGetsAnAdminWhoMustChangeTheirPassword() {
        // The application already ran this at startup, against the same empty database.
        dsl.truncate(LOCAL_USER).cascade().execute();

        new InitialAdmin(localUserDao, encoder).run(new DefaultApplicationArguments());

        final var admin = localUserDao.findByUsername("admin").orElseThrow();
        assertThat(admin.role()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.state()).isEqualTo(AccountState.PASSWORD_CHANGE_REQUIRED);
        assertThat(encoder.matches("admin", admin.passwordHash())).isTrue();
    }

    @Test
    void noAdminIsCreatedOnceUsersExist() {
        dsl.truncate(LOCAL_USER).cascade().execute();
        new TestLocalUsers(localUserDao, encoder).create("alice", UserRole.USER);

        new InitialAdmin(localUserDao, encoder).run(new DefaultApplicationArguments());

        assertThat(localUserDao.findByUsername("admin")).isEmpty();
    }

    @Test
    void theDevProfileGetsAnActiveAdminAndUser() {
        dsl.truncate(LOCAL_USER).cascade().execute();

        new DevUsers(localUserDao, encoder).run(new DefaultApplicationArguments());

        final var admin = localUserDao.findByUsername("admin").orElseThrow();
        final var user = localUserDao.findByUsername("user").orElseThrow();
        assertThat(admin.role()).isEqualTo(UserRole.ADMIN);
        assertThat(user.role()).isEqualTo(UserRole.USER);
        assertThat(admin.state()).isEqualTo(AccountState.ACTIVE);
        assertThat(encoder.matches("user", user.passwordHash())).isTrue();
    }

    @Test
    void theDevProfileResetsTheDevUsers() {
        dsl.truncate(LOCAL_USER).cascade().execute();
        final var existing = new TestLocalUsers(localUserDao, encoder).create("user", UserRole.ADMIN, AccountState.DISABLED);

        new DevUsers(localUserDao, encoder).run(new DefaultApplicationArguments());

        final var user = localUserDao.findByUsername("user").orElseThrow();
        assertThat(user.userId()).isEqualTo(existing.userId());
        assertThat(user.role()).isEqualTo(UserRole.USER);
        assertThat(user.state()).isEqualTo(AccountState.ACTIVE);
    }
}
