package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.OidcSession;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class OidcSessionDaoTest {

    @Autowired
    OidcSessionDao dao;

    @Autowired
    UserDao userDao;

    Users alice;

    @BeforeEach
    void setUp() {
        alice = new Users(uuidv7(), UserType.OIDC, "alice", UserRole.USER, AccountState.ACTIVE);
        userDao.insert(alice);
    }

    @Test
    void upsertInsertsAndReplacesTheRefreshToken() {
        final var familyId = uuidv7();
        final var first = new OidcSession(familyId, alice.userId(), "first");
        final var rotated = new OidcSession(familyId, alice.userId(), "rotated");

        dao.upsert(first);
        assertThat(dao.loadByFamilyId(familyId)).contains(first);

        dao.upsert(rotated);
        assertThat(dao.loadByFamilyId(familyId)).contains(rotated);
    }

    @Test
    void deleteRemovesOnlyThatSession() {
        final var laptop = new OidcSession(uuidv7(), alice.userId(), "laptop");
        final var phone = new OidcSession(uuidv7(), alice.userId(), "phone");
        dao.upsert(laptop);
        dao.upsert(phone);

        dao.delete(laptop.familyId());

        assertThat(dao.loadByFamilyId(laptop.familyId())).isEmpty();
        assertThat(dao.loadByFamilyId(phone.familyId())).contains(phone);
    }
}
