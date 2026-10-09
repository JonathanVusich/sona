package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.RefreshTokenMapper;
import org.sona.model.tables.pojos.RefreshToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.sona.model.Tables.REFRESH_TOKEN;

@Component
@RequiredArgsConstructor
@Transactional
public class RefreshTokenDao {

    private final DSLContext dsl;
    private final RefreshTokenMapper mapper;

    public void insert(RefreshToken token) {
        dsl.insertInto(REFRESH_TOKEN)
                .set(mapper.toRecord(token))
                .execute();
    }

    public Optional<RefreshToken> find(String tokenHash) {
        return dsl.selectFrom(REFRESH_TOKEN)
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .fetchOptional(mapper::fromRecord);
    }

    /**
     * Revokes the token if it is still valid. Checking and revoking in one statement means two concurrent refreshes
     * can't both use the same token.
     *
     * @return the token, now revoked, or empty if it was unknown, expired or already revoked
     */
    public Optional<RefreshToken> revokeIfValid(String tokenHash) {
        return dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, currentOffsetDateTime())
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .and(REFRESH_TOKEN.EXPIRES_AT.gt(currentOffsetDateTime()))
                .returning()
                .fetchOptional(mapper::fromRecord);
    }

    public void revokeFamily(UUID familyId) {
        dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, currentOffsetDateTime())
                .where(REFRESH_TOKEN.FAMILY_ID.eq(familyId))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .execute();
    }

    public void revokeAll(UUID userId) {
        dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, currentOffsetDateTime())
                .where(REFRESH_TOKEN.USER_ID.eq(userId))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .execute();
    }
}
