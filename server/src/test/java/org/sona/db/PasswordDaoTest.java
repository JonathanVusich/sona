package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.PasswordState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Password;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class PasswordDaoTest {

    @Autowired
    PasswordDao dao;

    @Autowired
    UserDao userDao;

    Users alice;

    @BeforeEach
    void setUp() {
        alice = new Users(uuidv7(), UserType.LOCAL, "alice", UserRole.USER, AccountState.ACTIVE);
        userDao.insert(alice);
    }

    @Test
    void upsertInsertsAndOverwrites() {
        final var first = new Password(alice.userId(), "{noop}first", PasswordState.VALID);
        final var second = new Password(alice.userId(), "{noop}second", PasswordState.CHANGE_REQUIRED);

        dao.upsert(first);
        assertThat(dao.loadByUserId(alice.userId())).contains(first);

        dao.upsert(second);
        assertThat(dao.loadByUserId(alice.userId())).contains(second);
    }

    @Test
    void updateEndsARequiredPasswordChange() {
        dao.upsert(new Password(alice.userId(), "{noop}old", PasswordState.CHANGE_REQUIRED));

        dao.updateHash(alice.userId(), "{noop}new");

        assertThat(dao.loadByUserId(alice.userId()))
                .contains(new Password(alice.userId(), "{noop}new", PasswordState.VALID));
    }

    @Test
    void aUserWithoutAPasswordHasNone() {
        assertThat(dao.loadByUserId(alice.userId())).isEmpty();
    }
}
