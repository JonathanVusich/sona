package org.sona.db;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.selectFrom;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.RELEASE_GROUP;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.sona.mappers.ArtistDetailsMapper;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.ReleaseGroupMapper;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every table the query reads is converted to its pojo by its table's mapper as it's read, so the composite mapper
 * builds its result from pojos only.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ArtistDetailsDao {

    private final DSLContext dsl;
    private final ArtistMapper artistMapper;
    private final ReleaseGroupMapper releaseGroupMapper;
    private final ArtistDetailsMapper detailsMapper;

    public Optional<ArtistDetails> loadById(final UUID artistId) {
        return dsl.select(ARTIST.convertFrom(artistMapper::fromRecord), releaseGroups())
                .from(ARTIST)
                .where(ARTIST.ARTIST_ID.eq(artistId))
                .fetchOptional(artist -> detailsMapper.fromPojos(artist.value1(), artist.value2()));
    }

    /**
     * The artist's release groups in the library, those with a release stored, newest first, and those with no known
     * date last.
     */
    private Field<List<ReleaseGroup>> releaseGroups() {
        return multiset(selectFrom(RELEASE_GROUP)
                        .where(RELEASE_GROUP.ARTIST_ID.eq(ARTIST.ARTIST_ID))
                        .and(RELEASE_GROUP.RELEASE_COUNT.gt(0))
                        .orderBy(RELEASE_GROUP.FIRST_RELEASE_DATE.desc().nullsLast(), RELEASE_GROUP.RELEASE_GROUP_ID))
                .convertFrom(groups -> groups.map(releaseGroupMapper::fromRecord));
    }
}
