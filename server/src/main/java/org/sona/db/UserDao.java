package org.sona.db;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.OIDC_IDENTITY;
import static org.sona.model.Tables.PASSWORD;
import static org.sona.model.Tables.USERS;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.OidcIdentityMapper;
import org.sona.mappers.PasswordMapper;
import org.sona.mappers.UsersMapper;
import org.sona.model.enums.PasswordState;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.OidcIdentity;
import org.sona.model.tables.pojos.Password;
import org.sona.model.tables.pojos.Users;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every user, however they sign in, and how they do: the password Sona keeps for a local user, or the OIDC provider
 * an OIDC user comes from.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class UserDao {

    private final DSLContext dsl;
    private final UsersMapper usersMapper;
    private final PasswordMapper passwordMapper;
    private final OidcIdentityMapper oidcIdentityMapper;

    public Optional<Users> loadById(final UUID userId) {
        return dsl.selectFrom(USERS).where(USERS.USER_ID.eq(userId)).fetchOptional(usersMapper::fromRecord);
    }

    public Optional<Users> loadByUsername(final String username) {
        return dsl.selectFrom(USERS).where(USERS.USERNAME.eq(username)).fetchOptional(usersMapper::fromRecord);
    }

    public boolean hasNoUsers() {
        return !dsl.fetchExists(USERS);
    }

    public void insert(final Users user) {
        dsl.insertInto(USERS).set(usersMapper.toRecord(user)).execute();
    }

    /**
     * Sets the role, which for an OIDC user follows the one their provider gives them each time they sign in.
     */
    public void updateRole(final UUID userId, final UserRole role) {
        dsl.update(USERS).set(USERS.ROLE, role).where(USERS.USER_ID.eq(userId)).execute();
    }

    /**
     * Inserts the user, or overwrites the role and state of the stored one with the same username.
     *
     * @return the stored user, which keeps its original ID if it already existed
     */
    public Users upsert(final Users user) {
        final var record = dsl.insertInto(USERS)
                .set(usersMapper.toRecord(user))
                .onConflict(USERS.USERNAME)
                .doUpdate()
                .set(USERS.ROLE, excluded(USERS.ROLE))
                .set(USERS.STATE, excluded(USERS.STATE))
                .returning()
                .fetchSingle();
        return usersMapper.fromRecord(record);
    }

    public Optional<Password> loadPassword(final UUID userId) {
        return dsl.selectFrom(PASSWORD).where(PASSWORD.USER_ID.eq(userId)).fetchOptional(passwordMapper::fromRecord);
    }

    /**
     * Inserts the user's password, or overwrites their stored one.
     */
    public void upsertPassword(final Password password) {
        dsl.insertInto(PASSWORD)
                .set(passwordMapper.toRecord(password))
                .onConflict(PASSWORD.USER_ID)
                .doUpdate()
                .set(PASSWORD.PASSWORD_HASH, excluded(PASSWORD.PASSWORD_HASH))
                .set(PASSWORD.STATE, excluded(PASSWORD.STATE))
                .execute();
    }

    /**
     * Sets a new password hash, which also ends a required password change.
     */
    public void updatePasswordHash(final UUID userId, final String passwordHash) {
        dsl.update(PASSWORD)
                .set(PASSWORD.PASSWORD_HASH, passwordHash)
                .set(PASSWORD.STATE, PasswordState.VALID)
                .where(PASSWORD.USER_ID.eq(userId))
                .execute();
    }

    public Optional<OidcIdentity> loadOidcIdentity(final UUID userId) {
        return dsl.selectFrom(OIDC_IDENTITY)
                .where(OIDC_IDENTITY.USER_ID.eq(userId))
                .fetchOptional(oidcIdentityMapper::fromRecord);
    }

    public void insertOidcIdentity(final OidcIdentity identity) {
        dsl.insertInto(OIDC_IDENTITY).set(oidcIdentityMapper.toRecord(identity)).execute();
    }
}
