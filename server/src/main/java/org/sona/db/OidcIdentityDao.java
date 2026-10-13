package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.OidcIdentityMapper;
import org.sona.model.tables.pojos.OidcIdentity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.sona.model.Tables.OIDC_IDENTITY;

/**
 * Where users who sign in through an OIDC provider come from.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class OidcIdentityDao {

    private final DSLContext dsl;
    private final OidcIdentityMapper mapper;

    public Optional<OidcIdentity> loadByUserId(final UUID userId) {
        return dsl.selectFrom(OIDC_IDENTITY)
                .where(OIDC_IDENTITY.USER_ID.eq(userId))
                .fetchOptional(mapper::fromRecord);
    }

    public void insert(final OidcIdentity identity) {
        dsl.insertInto(OIDC_IDENTITY)
                .set(mapper.toRecord(identity))
                .execute();
    }
}
