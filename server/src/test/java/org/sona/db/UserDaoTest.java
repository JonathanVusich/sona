package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sona.utils.IDGenerator.uuidv7;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.PasswordState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.OidcIdentity;
import org.sona.model.tables.pojos.Password;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

@IntegrationTest
class UserDaoTest {

    @Autowired
    UserDao dao;

    @Test
    void aLocalUserIsLoadedWithTheirPassword() {
        final var account = storeLocalUser("alice", AccountState.ACTIVE, PasswordState.VALID);

        assertThat(dao.loadById(account.user().userId())).contains(account);
        assertThat(dao.loadByUsername("alice")).contains(account);
    }

    @Test
    void anOidcUserIsLoadedWithTheirIdentity() {
        final var user = newUser("alice", UserType.OIDC, UserRole.USER, AccountState.ACTIVE);
        final var identity = new OidcIdentity(user.userId(), "https://id.example", "alice-id");

        assertThat(dao.insertIfAbsent(user, identity)).isTrue();

        assertThat(dao.loadById(user.userId())).contains(new OidcAccount(user, identity));
    }

    @Test
    void aDisabledUserIsNotLoadedAsActive() {
        final var active = storeLocalUser("alice", AccountState.ACTIVE, PasswordState.VALID);
        final var disabled = storeLocalUser("bob", AccountState.DISABLED, PasswordState.VALID);

        assertThat(dao.loadActiveById(active.user().userId())).contains(active);
        assertThat(dao.loadActiveById(disabled.user().userId())).isEmpty();
    }

    @Test
    void insertIfAbsentStoresNothingForATakenIdOrUsername() {
        final var alice = newUser("alice", UserType.OIDC, UserRole.USER, AccountState.ACTIVE);
        final var identity = new OidcIdentity(alice.userId(), "https://id.example", "alice-id");
        dao.insertIfAbsent(alice, identity);
        final var sameName = newUser("alice", UserType.OIDC, UserRole.ADMIN, AccountState.ACTIVE);

        assertThat(dao.insertIfAbsent(sameName, new OidcIdentity(sameName.userId(), "https://id.example", "other")))
                .isFalse();
        assertThat(dao.insertIfAbsent(alice, new OidcIdentity(alice.userId(), "https://other.example", "alice-id")))
                .isFalse();
        assertThat(dao.loadById(sameName.userId())).isEmpty();
        assertThat(dao.loadById(alice.userId())).contains(new OidcAccount(alice, identity));
    }

    @Test
    void anOidcIdentityBelongsToOneUser() {
        final var alice = newUser("alice", UserType.OIDC, UserRole.USER, AccountState.ACTIVE);
        dao.insertIfAbsent(alice, new OidcIdentity(alice.userId(), "https://id.example", "alice-id"));
        final var bob = newUser("bob", UserType.OIDC, UserRole.USER, AccountState.ACTIVE);
        final var copy = new OidcIdentity(bob.userId(), "https://id.example", "alice-id");

        assertThatThrownBy(() -> dao.insertIfAbsent(bob, copy)).isInstanceOf(DuplicateKeyException.class);
        assertThat(dao.loadById(bob.userId())).isEmpty();
    }

    @Test
    void hasNoUsersUntilOneIsInserted() {
        assertThat(dao.hasNoUsers()).isTrue();

        storeLocalUser("alice", AccountState.ACTIVE, PasswordState.VALID);

        assertThat(dao.hasNoUsers()).isFalse();
    }

    @Test
    void upsertOverwritesTheUserWithTheSameUsernameAndTheirPassword() {
        final var stored = storeLocalUser("admin", AccountState.DISABLED, PasswordState.CHANGE_REQUIRED);
        final var userId = stored.user().userId();

        dao.upsert(
                newUser("admin", UserType.LOCAL, UserRole.ADMIN, AccountState.ACTIVE),
                "{noop}new",
                PasswordState.VALID);

        assertThat(dao.loadByUsername("admin"))
                .contains(new LocalAccount(
                        new Users(userId, UserType.LOCAL, "admin", UserRole.ADMIN, AccountState.ACTIVE),
                        new Password(userId, "{noop}new", PasswordState.VALID)));
    }

    @Test
    void setRoleChangesOnlyTheRole() {
        final var stored = storeLocalUser("alice", AccountState.ACTIVE, PasswordState.VALID);
        final var userId = stored.user().userId();

        dao.setRole(userId, UserRole.ADMIN);

        assertThat(dao.loadById(userId).map(Account::user))
                .contains(new Users(userId, UserType.LOCAL, "alice", UserRole.ADMIN, AccountState.ACTIVE));
    }

    @Test
    void setPasswordHashEndsARequiredPasswordChange() {
        final var stored = storeLocalUser("alice", AccountState.ACTIVE, PasswordState.CHANGE_REQUIRED);
        final var userId = stored.user().userId();

        dao.setPasswordHash(userId, "{noop}new");

        assertThat(dao.loadById(userId))
                .contains(new LocalAccount(stored.user(), new Password(userId, "{noop}new", PasswordState.VALID)));
    }

    private LocalAccount storeLocalUser(
            final String username, final AccountState state, final PasswordState passwordState) {
        final var user = newUser(username, UserType.LOCAL, UserRole.USER, state);
        final var password = new Password(user.userId(), "{noop}" + username, passwordState);
        dao.insert(user, password);
        return new LocalAccount(user, password);
    }

    private static Users newUser(
            final String username, final UserType type, final UserRole role, final AccountState state) {
        return new Users(uuidv7(), type, username, role, state);
    }
}
