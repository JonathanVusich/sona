package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.UsersMapper;
import org.sona.model.enums.AccountState;
import org.sona.model.tables.pojos.Users;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.USERS;

@Component
@RequiredArgsConstructor
@Transactional
public class UserDao {

    private final DSLContext dsl;
    private final UsersMapper mapper;

    public Optional<Users> find(UUID userId) {
        return dsl.selectFrom(USERS)
                .where(USERS.USER_ID.eq(userId))
                .fetchOptional(mapper::fromRecord);
    }

    public Optional<Users> findByUsername(String username) {
        return dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOptional(mapper::fromRecord);
    }

    public boolean exists(String username) {
        return dsl.fetchExists(USERS, USERS.USERNAME.eq(username));
    }

    public boolean isEmpty() {
        return !dsl.fetchExists(USERS);
    }

    public void insert(Users user) {
        dsl.insertInto(USERS)
                .set(mapper.toRecord(user))
                .execute();
    }

    /**
     * Inserts the user, or overwrites the stored one with the same username with its password, role and state.
     *
     * @return the stored user, which keeps its original ID if it already existed
     */
    public Users upsert(Users user) {
        final var record = dsl.insertInto(USERS)
                .set(mapper.toRecord(user))
                .onConflict(USERS.USERNAME)
                .doUpdate()
                .set(USERS.PASSWORD_HASH, excluded(USERS.PASSWORD_HASH))
                .set(USERS.ROLE, excluded(USERS.ROLE))
                .set(USERS.STATE, excluded(USERS.STATE))
                .returning()
                .fetchSingle();
        return mapper.fromRecord(record);
    }

    /**
     * Sets a new password hash, which also ends a required password change.
     */
    public void updatePassword(UUID userId, String passwordHash) {
        dsl.update(USERS)
                .set(USERS.PASSWORD_HASH, passwordHash)
                .set(USERS.STATE, AccountState.ACTIVE)
                .where(USERS.USER_ID.eq(userId))
                .execute();
    }
}
