package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.LabelMapper;
import org.sona.mappers.ReleaseDetailsMapper;
import org.sona.mappers.ReleaseMapper;
import org.sona.model.tables.pojos.Label;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectFrom;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.LABEL;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_LABEL;

/**
 * Every table the query reads is converted to its pojo by its table's mapper as it's read, so the composite mappers
 * build their results from pojos only.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseDetailsDao {

    private final DSLContext dsl;
    private final ReleaseMapper releaseMapper;
    private final ArtistMapper artistMapper;
    private final LabelMapper labelMapper;
    private final ReleaseDetailsMapper detailsMapper;
    private final MediaFields mediaFields;

    /**
     * @return the release, if it's in the library: a release is only once one of its tracks is
     */
    public Optional<ReleaseDetails> loadById(final UUID releaseId) {
        return dsl.select(RELEASE.convertFrom(releaseMapper::fromRecord),
                        ARTIST.convertFrom(artistMapper::fromRecord), labels(),
                        mediaFields.releaseMedia(RELEASE.RELEASE_ID))
                .from(RELEASE)
                .join(ARTIST).on(ARTIST.ARTIST_ID.eq(RELEASE.ARTIST_ID))
                .where(RELEASE.RELEASE_ID.eq(releaseId))
                .and(RELEASE.TRACK_COUNT.gt(0))
                .fetchOptional(release -> detailsMapper.fromPojos(release.value1(), release.value2(),
                        release.value3(), release.value4()));
    }

    private Field<List<Label>> labels() {
        final var releaseLabelIds = select(RELEASE_LABEL.LABEL_ID)
                .from(RELEASE_LABEL)
                .where(RELEASE_LABEL.RELEASE_ID.eq(RELEASE.RELEASE_ID));
        return multiset(selectFrom(LABEL).where(LABEL.LABEL_ID.in(releaseLabelIds)).orderBy(LABEL.NAME))
                .convertFrom(labels -> labels.map(labelMapper::fromRecord));
    }
}
