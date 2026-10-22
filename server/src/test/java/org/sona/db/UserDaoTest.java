package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sona.utils.IDGenerator.uuidv7;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

@IntegrationTest
class UserDaoTest {

    @Autowired
    UserDao dao;

    @Test
    void insertAndFind() {
        final var user = newUser("alice", UserRole.USER, AccountState.ACTIVE);

        dao.insert(user);

        assertThat(dao.loadById(user.userId())).contains(user);
        assertThat(dao.loadByUsername("alice")).contains(user);
    }

    @Test
    void usernamesAreUnique() {
        dao.insert(newUser("alice", UserRole.USER, AccountState.ACTIVE));

        assertThatThrownBy(() -> dao.insert(newUser("alice", UserRole.ADMIN, AccountState.ACTIVE)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void hasNoUsersUntilOneIsInserted() {
        assertThat(dao.hasNoUsers()).isTrue();

        dao.insert(newUser("alice", UserRole.USER, AccountState.ACTIVE));

        assertThat(dao.hasNoUsers()).isFalse();
    }

    @Test
    void upsertOverwritesTheUserWithTheSameUsername() {
        final var stored = newUser("admin", UserRole.USER, AccountState.DISABLED);
        dao.insert(stored);

        final var upserted = dao.upsert(newUser("admin", UserRole.ADMIN, AccountState.ACTIVE));

        assertThat(upserted)
                .isEqualTo(new Users(stored.userId(), UserType.LOCAL, "admin", UserRole.ADMIN, AccountState.ACTIVE));
    }

    @Test
    void updateRoleChangesOnlyTheRole() {
        final var user = newUser("alice", UserRole.USER, AccountState.ACTIVE);
        dao.insert(user);

        dao.updateRole(user.userId(), UserRole.ADMIN);

        assertThat(dao.loadById(user.userId()))
                .contains(new Users(user.userId(), UserType.LOCAL, "alice", UserRole.ADMIN, AccountState.ACTIVE));
    }

    private static Users newUser(final String username, final UserRole role, final AccountState state) {
        return new Users(uuidv7(), UserType.LOCAL, username, role, state);
    }
}
