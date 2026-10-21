package org.sona.db;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_GROUP;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.DefaultReleaseMapper;
import org.sona.mappers.EditionMapper;
import org.sona.mappers.ReleaseGroupDetailsMapper;
import org.sona.mappers.ReleaseGroupMapper;
import org.sona.mappers.ReleaseMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final ReleaseGroupDetailsMapper detailsMapper;
    private final EditionMapper editionMapper;
    private final DefaultReleaseMapper defaultReleaseMapper;
    private final ReleaseFields releaseFields;

    /**
     * @return the release group, if it's in the library: a group is only once a release of it is stored
     */
    public Optional<ReleaseGroupDetails> loadById(final UUID releaseGroupId) {
        return dsl.select(
                        RELEASE_GROUP.convertFrom(releaseGroupMapper::fromRecord),
                        ARTIST.convertFrom(artistMapper::fromRecord),
                        defaultRelease(),
                        editions())
                .from(RELEASE_GROUP)
                .join(ARTIST)
                .on(ARTIST.ARTIST_ID.eq(RELEASE_GROUP.ARTIST_ID))
                .where(RELEASE_GROUP.RELEASE_GROUP_ID.eq(releaseGroupId))
                .and(RELEASE_GROUP.RELEASE_COUNT.gt(0))
                .fetchOptional(group ->
                        detailsMapper.fromPojos(group.value1(), group.value2(), group.value3(), group.value4()));
    }

    /**
     * The first of the group's {@link #editions()}, so a later deluxe or instrumental edition doesn't stand for the
     * group. {@link #loadById} only loads a group with a release in the library, so there is always one.
     */
    // TODO: Let users choose a group's default release, which this then prefers.
    private Field<DefaultRelease> defaultRelease() {
        return multiset(select(
                                RELEASE.convertFrom(releaseMapper::fromRecord),
                                releaseFields.releaseMediaWithTracks(RELEASE.RELEASE_ID))
                        .from(RELEASE)
                        .where(editionOfGroup())
                        .orderBy(editionOrder())
                        .limit(1))
                .convertFrom(releases -> releases.map(
                                found -> defaultReleaseMapper.fromPojos(found.value1(), found.value2()))
                        .getFirst());
    }

    private Field<List<Edition>> editions() {
        return multiset(select(
                                RELEASE.convertFrom(releaseMapper::fromRecord),
                                releaseFields.releaseLabels(RELEASE.RELEASE_ID),
                                releaseFields.releaseMedia(RELEASE.RELEASE_ID))
                        .from(RELEASE)
                        .where(editionOfGroup())
                        .orderBy(editionOrder()))
                .convertFrom(editions -> editions.map(
                        edition -> editionMapper.fromPojos(edition.value1(), edition.value2(), edition.value3())));
    }

    /**
     * A release of the group in the library: one with a track.
     */
    private static Condition editionOfGroup() {
        return RELEASE.RELEASE_GROUP_ID.eq(RELEASE_GROUP.RELEASE_GROUP_ID).and(RELEASE.TRACK_COUNT.gt(0));
    }

    /**
     * Earliest first, and those with no known date last.
     */
    private static List<SortField<?>> editionOrder() {
        return List.of(RELEASE.DATE.asc().nullsLast(), RELEASE.RELEASE_ID.asc());
    }
}
