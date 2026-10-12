package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.ReleaseGroupMapper;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.RELEASE_GROUP;

@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseGroupDao {

    private final DSLContext dsl;
    private final ReleaseGroupMapper mapper;

    /**
     * Inserts the release group, or overwrites the stored one with the same MusicBrainz ID with its newer data.
     *
     * @return the stored release group, which keeps its original ID if it already existed
     */
    public ReleaseGroup upsert(ReleaseGroup releaseGroup) {
        final var record = dsl.insertInto(RELEASE_GROUP)
                .set(mapper.toRecord(releaseGroup))
                .onConflict(RELEASE_GROUP.MUSICBRAINZ_RELEASE_GROUP_ID)
                .doUpdate()
                .set(RELEASE_GROUP.NAME, excluded(RELEASE_GROUP.NAME))
                .set(RELEASE_GROUP.TYPE, excluded(RELEASE_GROUP.TYPE))
                .set(RELEASE_GROUP.FIRST_RELEASE_DATE, excluded(RELEASE_GROUP.FIRST_RELEASE_DATE))
                .set(RELEASE_GROUP.ARTIST_ID, excluded(RELEASE_GROUP.ARTIST_ID))
                .returning()
                .fetchSingle();
        return mapper.fromRecord(record);
    }

    public Optional<ReleaseGroup> loadById(final UUID releaseGroupId) {
        return dsl.selectFrom(RELEASE_GROUP)
                .where(RELEASE_GROUP.RELEASE_GROUP_ID.eq(releaseGroupId))
                .fetchOptional(mapper::fromRecord);
    }
}
