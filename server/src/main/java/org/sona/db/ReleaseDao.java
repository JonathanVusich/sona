package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.ReleaseMapper;
import org.sona.model.tables.pojos.Release;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.RELEASE;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ReleaseDao {

    private final DSLContext dsl;
    private final ReleaseMapper mapper;

    /**
     * Inserts the release, or overwrites the stored one with the same MusicBrainz ID with its newer data.
     *
     * @return the stored release, which keeps its original ID if it already existed
     */
    public Release upsert(Release release) {
        final var record = dsl.insertInto(RELEASE)
                .set(mapper.toRecord(release))
                .onConflict(RELEASE.MUSICBRAINZ_RELEASE_ID)
                .doUpdate()
                .set(RELEASE.NAME, excluded(RELEASE.NAME))
                .set(RELEASE.RELEASE_GROUP_ID, excluded(RELEASE.RELEASE_GROUP_ID))
                .set(RELEASE.ARTIST_ID, excluded(RELEASE.ARTIST_ID))
                .returning()
                .fetchSingle();
        return mapper.fromRecord(record);
    }
}
