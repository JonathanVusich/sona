package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.OidcUserMapper;
import org.sona.model.tables.pojos.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.OIDC_USER;

@Component
@RequiredArgsConstructor
@Transactional
public class OidcUserDao {

    private final DSLContext dsl;
    private final OidcUserMapper mapper;

    public Optional<OidcUser> find(UUID userId) {
        return dsl.selectFrom(OIDC_USER)
                .where(OIDC_USER.USER_ID.eq(userId))
                .fetchOptional(mapper::fromRecord);
    }

    public Optional<OidcUser> findByUsername(String username) {
        return dsl.selectFrom(OIDC_USER)
                .where(OIDC_USER.USERNAME.eq(username))
                .fetchOptional(mapper::fromRecord);
    }

    /**
     * Inserts the user, or updates the stored one's role to the one their provider now gives. Their username is kept.
     *
     * @return the stored user
     */
    public OidcUser upsert(OidcUser user) {
        final var record = dsl.insertInto(OIDC_USER)
                .set(mapper.toRecord(user))
                .onConflict(OIDC_USER.USER_ID)
                .doUpdate()
                .set(OIDC_USER.ROLE, excluded(OIDC_USER.ROLE))
                .returning()
                .fetchSingle();
        return mapper.fromRecord(record);
    }
}
