package org.sona.db;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.OIDC_IDENTITY;
import static org.sona.model.Tables.PASSWORD;
import static org.sona.model.Tables.USERS;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.sona.mappers.OidcIdentityMapper;
import org.sona.mappers.PasswordMapper;
import org.sona.mappers.UsersMapper;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.PasswordState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.OidcIdentity;
import org.sona.model.tables.pojos.Password;
import org.sona.model.tables.pojos.Users;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every user, however they sign in, and how they do: the password Sona keeps for a local user, or the OIDC provider
 * an OIDC user comes from. A user is stored with one or the other, and read as an {@link Account} with it.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class UserDao {

    private final DSLContext dsl;
    private final UsersMapper usersMapper;
    private final PasswordMapper passwordMapper;
    private final OidcIdentityMapper oidcIdentityMapper;

    public Optional<Account> loadById(final UUID userId) {
        return loadAccount(USERS.USER_ID.eq(userId));
    }

    /**
     * @return the user, unless their account is disabled
     */
    public Optional<Account> loadActiveById(final UUID userId) {
        return loadAccount(USERS.USER_ID.eq(userId).and(USERS.STATE.eq(AccountState.ACTIVE)));
    }

    public Optional<Account> loadByUsername(final String username) {
        return loadAccount(USERS.USERNAME.eq(username));
    }

    public boolean hasNoUsers() {
        return !dsl.fetchExists(USERS);
    }

    /**
     * Stores a local user with their password.
     */
    public void insert(final Users user, final Password password) {
        dsl.insertInto(USERS).set(usersMapper.toRecord(user)).execute();
        dsl.insertInto(PASSWORD).set(passwordMapper.toRecord(password)).execute();
    }

    /**
     * Stores an OIDC user with their identity, unless their ID or username is taken.
     *
     * @return whether the user was stored
     */
    public boolean insertIfAbsent(final Users user, final OidcIdentity identity) {
        final var inserted = dsl.insertInto(USERS)
                .set(usersMapper.toRecord(user))
                .onConflictDoNothing()
                .execute();
        if (inserted == 0) {
            return false;
        }
        dsl.insertInto(OIDC_IDENTITY).set(oidcIdentityMapper.toRecord(identity)).execute();
        return true;
    }

    /**
     * Inserts a local user with their password, or overwrites the role and state of the stored one with the same
     * username, and their password.
     *
     * @param passwordHash the password's hash, kept for the stored user
     */
    public void upsert(final Users user, final String passwordHash, final PasswordState passwordState) {
        final var userId = dsl.insertInto(USERS)
                .set(usersMapper.toRecord(user))
                .onConflict(USERS.USERNAME)
                .doUpdate()
                .set(USERS.ROLE, excluded(USERS.ROLE))
                .set(USERS.STATE, excluded(USERS.STATE))
                .returning(USERS.USER_ID)
                .fetchSingle(USERS.USER_ID);
        dsl.insertInto(PASSWORD)
                .set(passwordMapper.toRecord(new Password(userId, passwordHash, passwordState)))
                .onConflict(PASSWORD.USER_ID)
                .doUpdate()
                .set(PASSWORD.PASSWORD_HASH, excluded(PASSWORD.PASSWORD_HASH))
                .set(PASSWORD.STATE, excluded(PASSWORD.STATE))
                .execute();
    }

    /**
     * Sets the role, which for an OIDC user follows the one their provider gives them each time they sign in.
     */
    public void setRole(final UUID userId, final UserRole role) {
        dsl.update(USERS).set(USERS.ROLE, role).where(USERS.USER_ID.eq(userId)).execute();
    }

    /**
     * Sets a new password hash, which also ends a required password change.
     */
    public void setPasswordHash(final UUID userId, final String passwordHash) {
        dsl.update(PASSWORD)
                .set(PASSWORD.PASSWORD_HASH, passwordHash)
                .set(PASSWORD.STATE, PasswordState.VALID)
                .where(PASSWORD.USER_ID.eq(userId))
                .execute();
    }

    private Optional<Account> loadAccount(final Condition users) {
        final var password = USERS.password();
        final var identity = USERS.oidcIdentity();
        return dsl.select(
                        USERS.convertFrom(usersMapper::fromRecord),
                        password.convertFrom(passwordMapper::fromRecord),
                        identity.convertFrom(oidcIdentityMapper::fromRecord))
                .from(USERS)
                .leftJoin(password)
                .leftJoin(identity)
                .where(users)
                .fetchOptional(record -> account(record.value1(), record.value2(), record.value3()));
    }

    /**
     * @param password null for an OIDC user, who has none
     * @param identity null for a local user, who has none
     */
    private static Account account(final Users user, final Password password, final OidcIdentity identity) {
        return switch (user.type()) {
            case LOCAL -> new LocalAccount(user, password);
            case OIDC -> new OidcAccount(user, identity);
        };
    }
}
