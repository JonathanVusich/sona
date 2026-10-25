package org.sona.db;

import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;
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
import org.sona.mappers.ReleaseGroupSummaryMapper;
import org.sona.mappers.ReleaseMapper;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Release groups, the albums list and the release group page. The list and the page show only the groups in the
 * library: those with a release stored. Every table a composite read selects is converted to its pojo by its table's
 * mapper as it's read, so the composite mappers build their results from pojos only.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseGroupDao {

    private final DSLContext dsl;
    private final ReleaseGroupMapper releaseGroupMapper;
    private final ArtistMapper artistMapper;
    private final ReleaseMapper releaseMapper;
    private final ReleaseGroupSummaryMapper summaryMapper;
    private final ReleaseGroupDetailsMapper detailsMapper;
    private final EditionMapper editionMapper;
    private final DefaultReleaseMapper defaultReleaseMapper;
    private final ReleaseFields releaseFields;

    /**
     * Inserts the release group, or overwrites the stored one with the same MusicBrainz ID with its newer data.
     *
     * @return the stored release group, which keeps its original ID if it already existed
     */
    public ReleaseGroup upsert(final ReleaseGroup releaseGroup) {
        final var record = dsl.insertInto(RELEASE_GROUP)
                .set(releaseGroupMapper.toRecord(releaseGroup))
                .onConflict(RELEASE_GROUP.MUSICBRAINZ_RELEASE_GROUP_ID)
                .doUpdate()
                .set(RELEASE_GROUP.NAME, excluded(RELEASE_GROUP.NAME))
                .set(RELEASE_GROUP.TYPE, excluded(RELEASE_GROUP.TYPE))
                .set(RELEASE_GROUP.FIRST_RELEASE_DATE, excluded(RELEASE_GROUP.FIRST_RELEASE_DATE))
                .set(RELEASE_GROUP.ARTIST_ID, excluded(RELEASE_GROUP.ARTIST_ID))
                .returning()
                .fetchSingle();
        return releaseGroupMapper.fromRecord(record);
    }

    public Optional<ReleaseGroup> loadById(final UUID releaseGroupId) {
        return dsl.selectFrom(RELEASE_GROUP)
                .where(RELEASE_GROUP.RELEASE_GROUP_ID.eq(releaseGroupId))
                .fetchOptional(releaseGroupMapper::fromRecord);
    }

    /**
     * @return the first {@code size} release groups with their artists, by name
     */
    public List<ReleaseGroupSummary> loadFirstPage(final int size) {
        return loadPage(noCondition(), size);
    }

    /**
     * Names repeat, so IDs break ties, and the page picks up after the name and ID of the last group on the page
     * before.
     *
     * @param after the last release group of the page before; a group that isn't stored has no page after it
     * @return up to {@code size} release groups with their artists, by name, that come after {@code after}
     */
    public List<ReleaseGroupSummary> loadNextPage(final UUID after, final int size) {
        return loadPage(afterReleaseGroup(after), size);
    }

    /**
     * @return the release group page's data, if the group is in the library
     */
    public Optional<ReleaseGroupDetails> loadDetails(final UUID releaseGroupId) {
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

    private List<ReleaseGroupSummary> loadPage(final Condition start, final int size) {
        return dsl.select(
                        RELEASE_GROUP.convertFrom(releaseGroupMapper::fromRecord),
                        ARTIST.convertFrom(artistMapper::fromRecord))
                .from(RELEASE_GROUP)
                .join(ARTIST)
                .on(ARTIST.ARTIST_ID.eq(RELEASE_GROUP.ARTIST_ID))
                .where(RELEASE_GROUP.RELEASE_COUNT.gt(0))
                .and(start)
                .orderBy(RELEASE_GROUP.NAME, RELEASE_GROUP.RELEASE_GROUP_ID)
                .limit(size)
                .fetch(group -> summaryMapper.fromPojos(group.value1(), group.value2()));
    }

    private static Condition afterReleaseGroup(final UUID after) {
        final var last = RELEASE_GROUP.as("last");
        final var lastKey = select(last.NAME, last.RELEASE_GROUP_ID).from(last).where(last.RELEASE_GROUP_ID.eq(after));
        return row(RELEASE_GROUP.NAME, RELEASE_GROUP.RELEASE_GROUP_ID).gt(lastKey);
    }

    /**
     * The first of the group's {@link #editions()}, so a later deluxe or instrumental edition doesn't stand for the
     * group. {@link #loadDetails} only loads a group with a release in the library, so there is always one.
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
