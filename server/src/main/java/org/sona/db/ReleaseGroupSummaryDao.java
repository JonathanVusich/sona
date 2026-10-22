package org.sona.db;

import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.RELEASE_GROUP;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.ReleaseGroupMapper;
import org.sona.mappers.ReleaseGroupSummaryMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the release groups in the library: those with a release stored.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseGroupSummaryDao {

    private final DSLContext dsl;
    private final ReleaseGroupMapper releaseGroupMapper;
    private final ArtistMapper artistMapper;
    private final ReleaseGroupSummaryMapper summaryMapper;

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
}
