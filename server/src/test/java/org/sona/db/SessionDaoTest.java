package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.OidcSession;
import org.sona.model.tables.pojos.RefreshToken;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class SessionDaoTest {

    @Autowired
    SessionDao dao;

    @Autowired
    UserDao userDao;

    Users alice;

    @BeforeEach
    void setUp() {
        alice = new Users(uuidv7(), UserType.LOCAL, "alice", UserRole.USER, AccountState.ACTIVE);
        userDao.insert(alice);
    }

    @Test
    void revokeIfValidRevokesAValidToken() {
        final var token = newToken(uuidv7(), "valid", OffsetDateTime.now().plusDays(1));
        dao.insertRefreshToken(token);

        final var revoked = dao.revokeIfValid("valid").orElseThrow();

        assertThat(revoked.refreshTokenId()).isEqualTo(token.refreshTokenId());
        assertThat(revoked.revokedAt()).isNotNull();
        assertThat(dao.loadRefreshTokenByHash("valid")).contains(revoked);
    }

    @Test
    void revokeIfValidRefusesAnExpiredToken() {
        dao.insertRefreshToken(
                newToken(uuidv7(), "expired", OffsetDateTime.now().minusMinutes(1)));

        assertThat(dao.revokeIfValid("expired")).isEmpty();
    }

    @Test
    void revokeIfValidRefusesARevokedToken() {
        dao.insertRefreshToken(newToken(uuidv7(), "used", OffsetDateTime.now().plusDays(1)));
        dao.revokeIfValid("used");

        assertThat(dao.revokeIfValid("used")).isEmpty();
    }

    @Test
    void revokeFamilyLeavesOtherFamiliesAlone() {
        final var family = uuidv7();
        dao.insertRefreshToken(newToken(family, "first", OffsetDateTime.now().plusDays(1)));
        dao.insertRefreshToken(newToken(family, "second", OffsetDateTime.now().plusDays(1)));
        dao.insertRefreshToken(newToken(uuidv7(), "other", OffsetDateTime.now().plusDays(1)));

        dao.revokeFamily(family);

        assertThat(dao.revokeIfValid("first")).isEmpty();
        assertThat(dao.revokeIfValid("second")).isEmpty();
        assertThat(dao.revokeIfValid("other")).isPresent();
    }

    @Test
    void revokeAllRevokesOnlyTheUsersTokens() {
        final var bob = new Users(uuidv7(), UserType.LOCAL, "bob", UserRole.USER, AccountState.ACTIVE);
        userDao.insert(bob);
        dao.insertRefreshToken(
                newToken(uuidv7(), "alice's", OffsetDateTime.now().plusDays(1)));
        dao.insertRefreshToken(new RefreshToken(
                uuidv7(), bob.userId(), uuidv7(), "bob's", OffsetDateTime.now().plusDays(1), null));

        dao.revokeAll(alice.userId());

        assertThat(dao.revokeIfValid("alice's")).isEmpty();
        assertThat(dao.revokeIfValid("bob's")).isPresent();
    }

    @Test
    void revokeFamilyTokenIfRecentlyReplacedRevokesTheFamilysValidToken() {
        final var family = uuidv7();
        dao.insertRefreshToken(newToken(family, "replaced", OffsetDateTime.now().plusDays(1)));
        dao.revokeIfValid("replaced");
        final var replacement =
                newToken(family, "replacement", OffsetDateTime.now().plusDays(1));
        dao.insertRefreshToken(replacement);

        final var revoked = dao.revokeFamilyTokenIfRecentlyReplaced(
                        "replaced", OffsetDateTime.now().minusMinutes(1))
                .orElseThrow();

        assertThat(revoked.refreshTokenId()).isEqualTo(replacement.refreshTokenId());
        assertThat(dao.revokeIfValid("replacement")).isEmpty();
    }

    @Test
    void revokeFamilyTokenIfRecentlyReplacedRefusesATokenReplacedEarlier() {
        final var family = uuidv7();
        dao.insertRefreshToken(newToken(family, "replaced", OffsetDateTime.now().plusDays(1)));
        dao.revokeIfValid("replaced");
        dao.insertRefreshToken(
                newToken(family, "replacement", OffsetDateTime.now().plusDays(1)));

        assertThat(dao.revokeFamilyTokenIfRecentlyReplaced(
                        "replaced", OffsetDateTime.now().plusMinutes(1)))
                .isEmpty();
        assertThat(dao.revokeIfValid("replacement")).isPresent();
    }

    @Test
    void revokeFamilyTokenIfRecentlyReplacedRefusesAnEndedFamily() {
        final var family = uuidv7();
        dao.insertRefreshToken(newToken(family, "replaced", OffsetDateTime.now().plusDays(1)));
        dao.revokeIfValid("replaced");
        dao.insertRefreshToken(
                newToken(family, "replacement", OffsetDateTime.now().plusDays(1)));
        dao.revokeFamily(family);

        assertThat(dao.revokeFamilyTokenIfRecentlyReplaced(
                        "replaced", OffsetDateTime.now().minusMinutes(1)))
                .isEmpty();
    }

    @Test
    void upsertOidcSessionInsertsAndReplacesTheRefreshToken() {
        final var userId = oidcUser("carol").userId();
        final var familyId = uuidv7();
        final var first = new OidcSession(familyId, userId, "first");
        final var rotated = new OidcSession(familyId, userId, "rotated");

        dao.upsertOidcSession(first);
        assertThat(dao.loadOidcSession(familyId)).contains(first);

        dao.upsertOidcSession(rotated);
        assertThat(dao.loadOidcSession(familyId)).contains(rotated);
    }

    @Test
    void deleteOidcSessionRemovesOnlyThatSession() {
        final var userId = oidcUser("carol").userId();
        final var laptop = new OidcSession(uuidv7(), userId, "laptop");
        final var phone = new OidcSession(uuidv7(), userId, "phone");
        dao.upsertOidcSession(laptop);
        dao.upsertOidcSession(phone);

        dao.deleteOidcSession(laptop.familyId());

        assertThat(dao.loadOidcSession(laptop.familyId())).isEmpty();
        assertThat(dao.loadOidcSession(phone.familyId())).contains(phone);
    }

    private Users oidcUser(final String username) {
        final var user = new Users(uuidv7(), UserType.OIDC, username, UserRole.USER, AccountState.ACTIVE);
        userDao.insert(user);
        return user;
    }

    private RefreshToken newToken(final UUID familyId, final String tokenHash, final OffsetDateTime expiresAt) {
        return new RefreshToken(uuidv7(), alice.userId(), familyId, tokenHash, expiresAt, null);
    }
}
