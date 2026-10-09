package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.OidcIdentity;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class OidcIdentityDaoTest {

    @Autowired
    OidcIdentityDao dao;

    @Autowired
    UserDao userDao;

    @Test
    void insertAndFind() {
        final var alice = user("alice");
        final var identity = new OidcIdentity(alice.userId(), "https://id.example", "alice-id");

        dao.insert(identity);

        assertThat(dao.find(alice.userId())).contains(identity);
    }

    @Test
    void anIdentityBelongsToOneUser() {
        dao.insert(new OidcIdentity(user("alice").userId(), "https://id.example", "alice-id"));
        final var copy = new OidcIdentity(user("bob").userId(), "https://id.example", "alice-id");

        assertThatThrownBy(() -> dao.insert(copy)).isInstanceOf(DuplicateKeyException.class);
    }

    private Users user(final String username) {
        final var user = new Users(uuidv7(), UserType.OIDC, username, UserRole.USER, AccountState.ACTIVE);
        userDao.insert(user);
        return user;
    }
}
