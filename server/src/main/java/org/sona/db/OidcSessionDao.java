package org.sona.db;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.OIDC_SESSION;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.OidcSessionMapper;
import org.sona.model.tables.pojos.OidcSession;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The provider's refresh token behind each session of an OIDC user.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class OidcSessionDao {

    private final DSLContext dsl;
    private final OidcSessionMapper mapper;

    public Optional<OidcSession> loadByFamilyId(final UUID familyId) {
        return dsl.selectFrom(OIDC_SESSION)
                .where(OIDC_SESSION.FAMILY_ID.eq(familyId))
                .fetchOptional(mapper::fromRecord);
    }

    /**
     * Inserts the session, or replaces the provider's refresh token of the stored one, which providers may rotate.
     */
    public void upsert(final OidcSession session) {
        dsl.insertInto(OIDC_SESSION)
                .set(mapper.toRecord(session))
                .onConflict(OIDC_SESSION.FAMILY_ID)
                .doUpdate()
                .set(OIDC_SESSION.REFRESH_TOKEN, excluded(OIDC_SESSION.REFRESH_TOKEN))
                .execute();
    }

    public void delete(final UUID familyId) {
        dsl.deleteFrom(OIDC_SESSION).where(OIDC_SESSION.FAMILY_ID.eq(familyId)).execute();
    }
}
