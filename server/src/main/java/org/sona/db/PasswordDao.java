package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.PasswordMapper;
import org.sona.model.enums.PasswordState;
import org.sona.model.tables.pojos.Password;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.PASSWORD;

/**
 * The passwords of users who sign in with one kept by Sona.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PasswordDao {

    private final DSLContext dsl;
    private final PasswordMapper mapper;

    public Optional<Password> findByUserId(UUID userId) {
        return dsl.selectFrom(PASSWORD)
                .where(PASSWORD.USER_ID.eq(userId))
                .fetchOptional(mapper::fromRecord);
    }

    /**
     * Inserts the user's password, or overwrites their stored one.
     */
    public void upsert(Password password) {
        dsl.insertInto(PASSWORD)
                .set(mapper.toRecord(password))
                .onConflict(PASSWORD.USER_ID)
                .doUpdate()
                .set(PASSWORD.PASSWORD_HASH, excluded(PASSWORD.PASSWORD_HASH))
                .set(PASSWORD.STATE, excluded(PASSWORD.STATE))
                .execute();
    }

    /**
     * Sets a new password hash, which also ends a required password change.
     */
    public void updateHash(UUID userId, String passwordHash) {
        dsl.update(PASSWORD)
                .set(PASSWORD.PASSWORD_HASH, passwordHash)
                .set(PASSWORD.STATE, PasswordState.VALID)
                .where(PASSWORD.USER_ID.eq(userId))
                .execute();
    }
}
