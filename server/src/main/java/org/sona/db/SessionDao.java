package org.sona.db;

import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.OIDC_SESSION;
import static org.sona.model.Tables.REFRESH_TOKEN;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.UpdateConditionStep;
import org.sona.mappers.OidcSessionMapper;
import org.sona.mappers.RefreshTokenMapper;
import org.sona.model.tables.pojos.OidcSession;
import org.sona.model.tables.pojos.RefreshToken;
import org.sona.model.tables.records.RefreshTokenRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessions: the refresh tokens of each session's family, and, for an OIDC user, the provider's refresh token behind
 * the session, which is stored with the session's first token and read with each token a refresh uses.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class SessionDao {

    private static final Condition UNEXPIRED = REFRESH_TOKEN.EXPIRES_AT.gt(currentOffsetDateTime());

    private static final Field<String> PROVIDER_REFRESH_TOKEN = field(select(OIDC_SESSION.REFRESH_TOKEN)
            .from(OIDC_SESSION)
            .where(OIDC_SESSION.FAMILY_ID.eq(REFRESH_TOKEN.FAMILY_ID)));

    private final DSLContext dsl;
    private final RefreshTokenMapper refreshTokenMapper;
    private final OidcSessionMapper oidcSessionMapper;

    public void insert(final RefreshToken token) {
        dsl.insertInto(REFRESH_TOKEN).set(refreshTokenMapper.toRecord(token)).execute();
    }

    /**
     * Stores the first token of an OIDC user's session, with the provider's refresh token behind the session.
     */
    public void insert(final RefreshToken token, final OidcSession session) {
        insert(token);
        dsl.insertInto(OIDC_SESSION).set(oidcSessionMapper.toRecord(session)).execute();
    }

    public Optional<RefreshToken> loadRevokedByHash(final String tokenHash) {
        return dsl.selectFrom(REFRESH_TOKEN)
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .and(REFRESH_TOKEN.REVOKED_AT.isNotNull())
                .fetchOptional(refreshTokenMapper::fromRecord);
    }

    /**
     * Revokes the token if it is still valid. Checking and revoking in one statement means two concurrent refreshes
     * can't both use the same token.
     *
     * @return the token, now revoked, or empty if it was unknown, expired or already revoked
     */
    public Optional<UsedToken> revokeIfValid(final String tokenHash) {
        return revoke(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .and(UNEXPIRED)
                .returningResult(usedTokenFields())
                .fetchOptional(this::usedToken);
    }

    /**
     * Revokes the valid token of this token's family, if this token was replaced after {@code replacedAfter}. A family
     * has one valid token at a time, as each refresh revokes the token it replaces.
     *
     * @return the family's valid token, now revoked, or empty if this token was replaced earlier or its family has ended
     */
    public Optional<UsedToken> revokeFamilyTokenIfRecentlyReplaced(
            final String tokenHash, final OffsetDateTime replacedAfter) {
        final var family = select(REFRESH_TOKEN.FAMILY_ID)
                .from(REFRESH_TOKEN)
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .and(REFRESH_TOKEN.REVOKED_AT.gt(replacedAfter));
        return revoke(REFRESH_TOKEN.FAMILY_ID.in(family))
                .and(UNEXPIRED)
                .returningResult(usedTokenFields())
                .fetchOptional(this::usedToken);
    }

    /**
     * Ends the session: revokes its tokens, and forgets the provider's refresh token behind it.
     */
    public void revokeFamily(final UUID familyId) {
        revoke(REFRESH_TOKEN.FAMILY_ID.eq(familyId)).execute();
        dsl.deleteFrom(OIDC_SESSION).where(OIDC_SESSION.FAMILY_ID.eq(familyId)).execute();
    }

    public void revokeAll(final UUID userId) {
        revoke(REFRESH_TOKEN.USER_ID.eq(userId)).execute();
    }

    /**
     * Replaces the provider's refresh token behind the session, which providers may rotate. A session that has ended
     * meanwhile keeps none.
     */
    public void setProviderRefreshToken(final UUID familyId, final String providerRefreshToken) {
        dsl.update(OIDC_SESSION)
                .set(OIDC_SESSION.REFRESH_TOKEN, providerRefreshToken)
                .where(OIDC_SESSION.FAMILY_ID.eq(familyId))
                .execute();
    }

    /**
     * Revokes the tokens that aren't revoked yet, now.
     */
    private UpdateConditionStep<RefreshTokenRecord> revoke(final Condition tokens) {
        return dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, currentOffsetDateTime())
                .where(tokens)
                .and(REFRESH_TOKEN.REVOKED_AT.isNull());
    }

    private static SelectField<?>[] usedTokenFields() {
        return Stream.concat(Stream.of(REFRESH_TOKEN.fields()), Stream.of(PROVIDER_REFRESH_TOKEN))
                .toArray(SelectField<?>[]::new);
    }

    private UsedToken usedToken(final Record record) {
        final var token = refreshTokenMapper.fromRecord(record.into(REFRESH_TOKEN));
        return new UsedToken(token, record.get(PROVIDER_REFRESH_TOKEN));
    }
}
