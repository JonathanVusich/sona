package org.sona.db;

import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.OIDC_SESSION;
import static org.sona.model.Tables.REFRESH_TOKEN;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.OidcSessionMapper;
import org.sona.mappers.RefreshTokenMapper;
import org.sona.model.tables.pojos.OidcSession;
import org.sona.model.tables.pojos.RefreshToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessions: the refresh tokens of each session's family, and, for an OIDC user, the provider's refresh token behind
 * the session.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class SessionDao {

    private final DSLContext dsl;
    private final RefreshTokenMapper refreshTokenMapper;
    private final OidcSessionMapper oidcSessionMapper;

    public void insertRefreshToken(final RefreshToken token) {
        dsl.insertInto(REFRESH_TOKEN).set(refreshTokenMapper.toRecord(token)).execute();
    }

    public Optional<RefreshToken> loadRefreshTokenByHash(final String tokenHash) {
        return dsl.selectFrom(REFRESH_TOKEN)
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .fetchOptional(refreshTokenMapper::fromRecord);
    }

    /**
     * Revokes the token if it is still valid. Checking and revoking in one statement means two concurrent refreshes
     * can't both use the same token.
     *
     * @return the token, now revoked, or empty if it was unknown, expired or already revoked
     */
    public Optional<RefreshToken> revokeIfValid(final String tokenHash) {
        return dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, currentOffsetDateTime())
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .and(REFRESH_TOKEN.EXPIRES_AT.gt(currentOffsetDateTime()))
                .returning()
                .fetchOptional(refreshTokenMapper::fromRecord);
    }

    /**
     * Revokes the valid token of this token's family, if this token was replaced after {@code replacedAfter}. A family
     * has one valid token at a time, as each refresh revokes the token it replaces.
     *
     * @return the family's valid token, now revoked, or empty if this token was replaced earlier or its family has ended
     */
    public Optional<RefreshToken> revokeFamilyTokenIfRecentlyReplaced(
            final String tokenHash, final OffsetDateTime replacedAfter) {
        final var family = select(REFRESH_TOKEN.FAMILY_ID)
                .from(REFRESH_TOKEN)
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .and(REFRESH_TOKEN.REVOKED_AT.gt(replacedAfter));
        return dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, currentOffsetDateTime())
                .where(REFRESH_TOKEN.FAMILY_ID.in(family))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .and(REFRESH_TOKEN.EXPIRES_AT.gt(currentOffsetDateTime()))
                .returning()
                .fetchOptional(refreshTokenMapper::fromRecord);
    }

    public void revokeFamily(final UUID familyId) {
        dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, currentOffsetDateTime())
                .where(REFRESH_TOKEN.FAMILY_ID.eq(familyId))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .execute();
    }

    public void revokeAll(final UUID userId) {
        dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, currentOffsetDateTime())
                .where(REFRESH_TOKEN.USER_ID.eq(userId))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .execute();
    }

    public Optional<OidcSession> loadOidcSession(final UUID familyId) {
        return dsl.selectFrom(OIDC_SESSION)
                .where(OIDC_SESSION.FAMILY_ID.eq(familyId))
                .fetchOptional(oidcSessionMapper::fromRecord);
    }

    /**
     * Inserts the session, or replaces the provider's refresh token of the stored one, which providers may rotate.
     */
    public void upsertOidcSession(final OidcSession session) {
        dsl.insertInto(OIDC_SESSION)
                .set(oidcSessionMapper.toRecord(session))
                .onConflict(OIDC_SESSION.FAMILY_ID)
                .doUpdate()
                .set(OIDC_SESSION.REFRESH_TOKEN, excluded(OIDC_SESSION.REFRESH_TOKEN))
                .execute();
    }

    public void deleteOidcSession(final UUID familyId) {
        dsl.deleteFrom(OIDC_SESSION).where(OIDC_SESSION.FAMILY_ID.eq(familyId)).execute();
    }
}
