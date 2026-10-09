package org.sona.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.RefreshToken;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class RefreshTokenDaoTest {

    @Autowired
    RefreshTokenDao dao;

    @Autowired
    UserDao userDao;

    Users alice;

    @BeforeEach
    void setUp() {
        alice = new Users(uuidv7(), "alice", "{noop}alice", UserRole.USER, AccountState.ACTIVE, null, null);
        userDao.insert(alice);
    }

    @Test
    void revokeIfValidRevokesAValidToken() {
        final var token = token(uuidv7(), "valid", OffsetDateTime.now().plusDays(1));
        dao.insert(token);

        final var revoked = dao.revokeIfValid("valid").orElseThrow();

        assertThat(revoked.refreshTokenId()).isEqualTo(token.refreshTokenId());
        assertThat(revoked.revokedAt()).isNotNull();
        assertThat(dao.find("valid")).contains(revoked);
    }

    @Test
    void revokeIfValidRefusesAnExpiredToken() {
        dao.insert(token(uuidv7(), "expired", OffsetDateTime.now().minusMinutes(1)));

        assertThat(dao.revokeIfValid("expired")).isEmpty();
    }

    @Test
    void revokeIfValidRefusesARevokedToken() {
        dao.insert(token(uuidv7(), "used", OffsetDateTime.now().plusDays(1)));
        dao.revokeIfValid("used");

        assertThat(dao.revokeIfValid("used")).isEmpty();
    }

    @Test
    void revokeFamilyLeavesOtherFamiliesAlone() {
        final var family = uuidv7();
        dao.insert(token(family, "first", OffsetDateTime.now().plusDays(1)));
        dao.insert(token(family, "second", OffsetDateTime.now().plusDays(1)));
        dao.insert(token(uuidv7(), "other", OffsetDateTime.now().plusDays(1)));

        dao.revokeFamily(family);

        assertThat(dao.revokeIfValid("first")).isEmpty();
        assertThat(dao.revokeIfValid("second")).isEmpty();
        assertThat(dao.revokeIfValid("other")).isPresent();
    }

    @Test
    void revokeAllRevokesOnlyTheUsersTokens() {
        final var bob = new Users(uuidv7(), "bob", "{noop}bob", UserRole.USER, AccountState.ACTIVE, null, null);
        userDao.insert(bob);
        dao.insert(token(uuidv7(), "alice's", OffsetDateTime.now().plusDays(1)));
        dao.insert(new RefreshToken(uuidv7(), bob.userId(), uuidv7(), "bob's", OffsetDateTime.now().plusDays(1), null));

        dao.revokeAll(alice.userId());

        assertThat(dao.revokeIfValid("alice's")).isEmpty();
        assertThat(dao.revokeIfValid("bob's")).isPresent();
    }

    private RefreshToken token(final UUID familyId, final String tokenHash, final OffsetDateTime expiresAt) {
        return new RefreshToken(uuidv7(), alice.userId(), familyId, tokenHash, expiresAt, null);
    }
}
