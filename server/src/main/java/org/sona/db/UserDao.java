package org.sona.db;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.USERS;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.UsersMapper;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Users;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every user, however they sign in. {@link PasswordDao} and {@link OidcIdentityDao} keep how they sign in.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class UserDao {

    private final DSLContext dsl;
    private final UsersMapper mapper;

    public Optional<Users> loadById(final UUID userId) {
        return dsl.selectFrom(USERS).where(USERS.USER_ID.eq(userId)).fetchOptional(mapper::fromRecord);
    }

    public Optional<Users> loadByUsername(final String username) {
        return dsl.selectFrom(USERS).where(USERS.USERNAME.eq(username)).fetchOptional(mapper::fromRecord);
    }

    public boolean hasNoUsers() {
        return !dsl.fetchExists(USERS);
    }

    public void insert(final Users user) {
        dsl.insertInto(USERS).set(mapper.toRecord(user)).execute();
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
                .set(mapper.toRecord(user))
                .onConflict(USERS.USERNAME)
                .doUpdate()
                .set(USERS.ROLE, excluded(USERS.ROLE))
                .set(USERS.STATE, excluded(USERS.STATE))
                .returning()
                .fetchSingle();
        return mapper.fromRecord(record);
    }
}
