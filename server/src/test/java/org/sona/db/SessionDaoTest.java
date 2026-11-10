package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.auth.TestUsers.insertUser;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.OidcIdentity;
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
        insertUser(userDao, alice);
    }

    @Test
    void revokeIfValidRevokesAValidToken() {
        final var token = newToken(uuidv7(), "valid", OffsetDateTime.now().plusDays(1));
        dao.insert(token);

        final var revoked = dao.revokeIfValid("valid").orElseThrow();

        assertThat(revoked.token().refreshTokenId()).isEqualTo(token.refreshTokenId());
        assertThat(revoked.token().revokedAt()).isNotNull();
        assertThat(dao.loadRevokedByHash("valid")).contains(revoked.token());
        assertThat(revoked.providerRefreshToken()).isNull();
    }

    @Test
    void revokeIfValidRefusesAnExpiredToken() {
        dao.insert(newToken(uuidv7(), "expired", OffsetDateTime.now().minusMinutes(1)));

        assertThat(dao.revokeIfValid("expired")).isEmpty();
    }

    @Test
    void revokeIfValidRefusesARevokedToken() {
        dao.insert(newToken(uuidv7(), "used", OffsetDateTime.now().plusDays(1)));
        dao.revokeIfValid("used");

        assertThat(dao.revokeIfValid("used")).isEmpty();
    }

    @Test
    void revokeFamilyLeavesOtherFamiliesAlone() {
        final var family = uuidv7();
        dao.insert(newToken(family, "first", OffsetDateTime.now().plusDays(1)));
        dao.insert(newToken(family, "second", OffsetDateTime.now().plusDays(1)));
        dao.insert(newToken(uuidv7(), "other", OffsetDateTime.now().plusDays(1)));

        dao.revokeFamily(family);

        assertThat(dao.revokeIfValid("first")).isEmpty();
        assertThat(dao.revokeIfValid("second")).isEmpty();
        assertThat(dao.revokeIfValid("other")).isPresent();
    }

    @Test
    void revokeAllRevokesOnlyTheUsersTokens() {
        final var bob = new Users(uuidv7(), UserType.LOCAL, "bob", UserRole.USER, AccountState.ACTIVE);
        insertUser(userDao, bob);
        dao.insert(newToken(uuidv7(), "alice's", OffsetDateTime.now().plusDays(1)));
        dao.insert(new RefreshToken(
                uuidv7(), bob.userId(), uuidv7(), "bob's", OffsetDateTime.now().plusDays(1), null));

        dao.revokeAll(alice.userId());

        assertThat(dao.revokeIfValid("alice's")).isEmpty();
        assertThat(dao.revokeIfValid("bob's")).isPresent();
    }

    @Test
    void revokeFamilyTokenIfRecentlyReplacedRevokesTheFamilysValidToken() {
        final var family = uuidv7();
        dao.insert(newToken(family, "replaced", OffsetDateTime.now().plusDays(1)));
        dao.revokeIfValid("replaced");
        final var replacement =
                newToken(family, "replacement", OffsetDateTime.now().plusDays(1));
        dao.insert(replacement);

        final var revoked = dao.revokeFamilyTokenIfRecentlyReplaced(
                        "replaced", OffsetDateTime.now().minusMinutes(1))
                .orElseThrow();

        assertThat(revoked.token().refreshTokenId()).isEqualTo(replacement.refreshTokenId());
        assertThat(dao.revokeIfValid("replacement")).isEmpty();
    }

    @Test
    void revokeFamilyTokenIfRecentlyReplacedRefusesATokenReplacedEarlier() {
        final var family = uuidv7();
        dao.insert(newToken(family, "replaced", OffsetDateTime.now().plusDays(1)));
        dao.revokeIfValid("replaced");
        dao.insert(newToken(family, "replacement", OffsetDateTime.now().plusDays(1)));

        assertThat(dao.revokeFamilyTokenIfRecentlyReplaced(
                        "replaced", OffsetDateTime.now().plusMinutes(1)))
                .isEmpty();
        assertThat(dao.revokeIfValid("replacement")).isPresent();
    }

    @Test
    void revokeFamilyTokenIfRecentlyReplacedRefusesAnEndedFamily() {
        final var family = uuidv7();
        dao.insert(newToken(family, "replaced", OffsetDateTime.now().plusDays(1)));
        dao.revokeIfValid("replaced");
        dao.insert(newToken(family, "replacement", OffsetDateTime.now().plusDays(1)));
        dao.revokeFamily(family);

        assertThat(dao.revokeFamilyTokenIfRecentlyReplaced(
                        "replaced", OffsetDateTime.now().minusMinutes(1)))
                .isEmpty();
    }

    @Test
    void aRevokedTokenComesWithTheProviderRefreshTokenBehindItsSession() {
        final var userId = oidcUser("carol").userId();
        final var familyId = uuidv7();
        dao.insert(
                new RefreshToken(
                        uuidv7(),
                        userId,
                        familyId,
                        "carol's",
                        OffsetDateTime.now().plusDays(1),
                        null),
                new OidcSession(familyId, userId, "provider's"));

        assertThat(dao.revokeIfValid("carol's").orElseThrow().providerRefreshToken())
                .isEqualTo("provider's");
    }

    @Test
    void setProviderRefreshTokenReplacesTheSessionsOne() {
        final var userId = oidcUser("carol").userId();
        final var familyId = uuidv7();
        dao.insert(
                new RefreshToken(
                        uuidv7(),
                        userId,
                        familyId,
                        "carol's",
                        OffsetDateTime.now().plusDays(1),
                        null),
                new OidcSession(familyId, userId, "first"));

        dao.setProviderRefreshToken(familyId, "rotated");

        assertThat(dao.revokeIfValid("carol's").orElseThrow().providerRefreshToken())
                .isEqualTo("rotated");
    }

    @Test
    void revokeFamilyForgetsTheProviderRefreshToken() {
        final var userId = oidcUser("carol").userId();
        final var familyId = uuidv7();
        dao.insert(
                new RefreshToken(
                        uuidv7(),
                        userId,
                        familyId,
                        "first",
                        OffsetDateTime.now().plusDays(1),
                        null),
                new OidcSession(familyId, userId, "provider's"));

        dao.revokeFamily(familyId);

        dao.insert(new RefreshToken(
                uuidv7(), userId, familyId, "later", OffsetDateTime.now().plusDays(1), null));
        assertThat(dao.revokeIfValid("later").orElseThrow().providerRefreshToken())
                .isNull();
    }

    private Users oidcUser(final String username) {
        final var user = new Users(uuidv7(), UserType.OIDC, username, UserRole.USER, AccountState.ACTIVE);
        userDao.insertIfAbsent(user, new OidcIdentity(user.userId(), "https://id.example", username));
        return user;
    }

    private RefreshToken newToken(final UUID familyId, final String tokenHash, final OffsetDateTime expiresAt) {
        return new RefreshToken(uuidv7(), alice.userId(), familyId, tokenHash, expiresAt, null);
    }
}
