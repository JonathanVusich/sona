package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class UserDaoTest {

    @Autowired
    UserDao dao;

    @Test
    void insertAndFind() {
        final var user = user("alice", UserRole.USER, AccountState.ACTIVE);

        dao.insert(user);

        assertThat(dao.find(user.userId())).contains(user);
        assertThat(dao.findByUsername("alice")).contains(user);
        assertThat(dao.exists("alice")).isTrue();
    }

    @Test
    void isEmptyUntilAUserIsInserted() {
        assertThat(dao.isEmpty()).isTrue();

        dao.insert(user("alice", UserRole.USER, AccountState.ACTIVE));

        assertThat(dao.isEmpty()).isFalse();
    }

    @Test
    void upsertOverwritesTheUserWithTheSameUsername() {
        final var stored = user("admin", UserRole.USER, AccountState.DISABLED);
        dao.insert(stored);

        final var upserted = dao.upsert(user("admin", UserRole.ADMIN, AccountState.ACTIVE));

        assertThat(upserted.userId()).isEqualTo(stored.userId());
        assertThat(upserted.role()).isEqualTo(UserRole.ADMIN);
        assertThat(upserted.state()).isEqualTo(AccountState.ACTIVE);
    }

    @Test
    void updatePasswordEndsARequiredPasswordChange() {
        final var user = user("admin", UserRole.ADMIN, AccountState.PASSWORD_CHANGE_REQUIRED);
        dao.insert(user);

        dao.updatePassword(user.userId(), "{noop}new");

        final var updated = dao.find(user.userId()).orElseThrow();
        assertThat(updated.passwordHash()).isEqualTo("{noop}new");
        assertThat(updated.state()).isEqualTo(AccountState.ACTIVE);
    }

    private static Users user(final String username, final UserRole role, final AccountState state) {
        return new Users(uuidv7(), username, "{noop}" + username, role, state, null, null);
    }
}
