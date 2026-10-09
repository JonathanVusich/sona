package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.LocalUserMapper;
import org.sona.model.enums.AccountState;
import org.sona.model.tables.pojos.LocalUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.LOCAL_USER;

@Component
@RequiredArgsConstructor
@Transactional
public class LocalUserDao {

    private final DSLContext dsl;
    private final LocalUserMapper mapper;

    public Optional<LocalUser> find(UUID userId) {
        return dsl.selectFrom(LOCAL_USER)
                .where(LOCAL_USER.USER_ID.eq(userId))
                .fetchOptional(mapper::fromRecord);
    }

    public Optional<LocalUser> findByUsername(String username) {
        return dsl.selectFrom(LOCAL_USER)
                .where(LOCAL_USER.USERNAME.eq(username))
                .fetchOptional(mapper::fromRecord);
    }

    public boolean exists(String username) {
        return dsl.fetchExists(LOCAL_USER, LOCAL_USER.USERNAME.eq(username));
    }

    public boolean isEmpty() {
        return !dsl.fetchExists(LOCAL_USER);
    }

    public void insert(LocalUser user) {
        dsl.insertInto(LOCAL_USER)
                .set(mapper.toRecord(user))
                .execute();
    }

    /**
     * Inserts the user, or overwrites the stored one with the same username with its password, role and state.
     *
     * @return the stored user, which keeps its original ID if it already existed
     */
    public LocalUser upsert(LocalUser user) {
        final var record = dsl.insertInto(LOCAL_USER)
                .set(mapper.toRecord(user))
                .onConflict(LOCAL_USER.USERNAME)
                .doUpdate()
                .set(LOCAL_USER.PASSWORD_HASH, excluded(LOCAL_USER.PASSWORD_HASH))
                .set(LOCAL_USER.ROLE, excluded(LOCAL_USER.ROLE))
                .set(LOCAL_USER.STATE, excluded(LOCAL_USER.STATE))
                .returning()
                .fetchSingle();
        return mapper.fromRecord(record);
    }

    /**
     * Sets a new password hash, which also ends a required password change.
     */
    public void updatePassword(UUID userId, String passwordHash) {
        dsl.update(LOCAL_USER)
                .set(LOCAL_USER.PASSWORD_HASH, passwordHash)
                .set(LOCAL_USER.STATE, AccountState.ACTIVE)
                .where(LOCAL_USER.USER_ID.eq(userId))
                .execute();
    }
}
