package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.DefaultReleaseMapper;
import org.sona.mappers.EditionMapper;
import org.sona.mappers.LabelMapper;
import org.sona.mappers.MediumMapper;
import org.sona.mappers.ReleaseGroupDetailsMapper;
import org.sona.mappers.ReleaseGroupMapper;
import org.sona.mappers.ReleaseMapper;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
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
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_GROUP;
import static org.sona.model.Tables.RELEASE_LABEL;

/**
 * Every table the query reads is converted to its pojo by its table's mapper as it's read, so the composite mappers
 * build their results from pojos only.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseGroupDetailsDao {

    private final DSLContext dsl;
    private final ReleaseGroupMapper releaseGroupMapper;
    private final ArtistMapper artistMapper;
    private final ReleaseMapper releaseMapper;
    private final LabelMapper labelMapper;
    private final MediumMapper mediumMapper;
    private final ReleaseGroupDetailsMapper detailsMapper;
    private final EditionMapper editionMapper;
    private final DefaultReleaseMapper defaultReleaseMapper;
    private final MediaFields mediaFields;

    public Optional<ReleaseGroupDetails> loadById(final UUID releaseGroupId) {
        return dsl.select(RELEASE_GROUP.convertFrom(releaseGroupMapper::fromRecord),
                        ARTIST.convertFrom(artistMapper::fromRecord), defaultRelease(), editions())
                .from(RELEASE_GROUP)
                .join(ARTIST).on(ARTIST.ARTIST_ID.eq(RELEASE_GROUP.ARTIST_ID))
                .where(RELEASE_GROUP.RELEASE_GROUP_ID.eq(releaseGroupId))
                .fetchOptional(group -> detailsMapper.fromPojos(group.value1(), group.value2(), group.value3(),
                        group.value4()));
    }

    /**
     * The group's earliest release, in the order {@link #editions()} lists them, so a later deluxe or instrumental
     * edition doesn't stand for the group. Null when none of its releases is stored.
     */
    // TODO: Let users choose a group's default release, which this then prefers.
    private Field<DefaultRelease> defaultRelease() {
        final var release = RELEASE.as("default_release");
        return multiset(select(release.convertFrom(releaseMapper::fromRecord),
                        mediaFields.releaseMedia(release.RELEASE_ID))
                .from(release)
                .where(release.RELEASE_GROUP_ID.eq(RELEASE_GROUP.RELEASE_GROUP_ID))
                .orderBy(release.DATE.asc().nullsLast(), release.RELEASE_ID)
                .limit(1))
                .convertFrom(releases -> releases.stream()
                        .map(found -> defaultReleaseMapper.fromPojos(found.value1(), found.value2()))
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Earliest first, and those with no known date last.
     */
    private Field<List<Edition>> editions() {
        return multiset(select(RELEASE.convertFrom(releaseMapper::fromRecord), labels(), media())
                .from(RELEASE)
                .where(RELEASE.RELEASE_GROUP_ID.eq(RELEASE_GROUP.RELEASE_GROUP_ID))
                .orderBy(RELEASE.DATE.asc().nullsLast(), RELEASE.RELEASE_ID))
                .convertFrom(editions -> editions.map(edition -> editionMapper.fromPojos(edition.value1(),
                        edition.value2(), edition.value3())));
    }

    private Field<List<Label>> labels() {
        final var releaseLabelIds = select(RELEASE_LABEL.LABEL_ID)
                .from(RELEASE_LABEL)
                .where(RELEASE_LABEL.RELEASE_ID.eq(RELEASE.RELEASE_ID));
        return multiset(selectFrom(LABEL).where(LABEL.LABEL_ID.in(releaseLabelIds)).orderBy(LABEL.NAME))
                .convertFrom(labels -> labels.map(labelMapper::fromRecord));
    }

    private Field<List<Medium>> media() {
        return multiset(selectFrom(MEDIUM).where(MEDIUM.RELEASE_ID.eq(RELEASE.RELEASE_ID)).orderBy(MEDIUM.POSITION))
                .convertFrom(media -> media.map(mediumMapper::fromRecord));
    }
}
