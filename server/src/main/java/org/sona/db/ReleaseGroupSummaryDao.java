package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.ReleaseGroupMapper;
import org.sona.mappers.ReleaseGroupSummaryMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.RELEASE_GROUP;

@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseGroupSummaryDao {

    private final DSLContext dsl;
    private final ReleaseGroupMapper releaseGroupMapper;
    private final ArtistMapper artistMapper;
    private final ReleaseGroupSummaryMapper summaryMapper;

    /**
     * Pages through the release groups by name. Names repeat, so IDs break ties, and each page picks up after the name
     * and ID of the last group on the page before.
     *
     * @param after the last release group of the page before, or null for the first page; a group that isn't stored
     *              has no page after it
     * @return up to {@code size} release groups with their artists, by name
     */
    public List<ReleaseGroupSummary> loadPage(final UUID after, final int size) {
        return dsl.select(RELEASE_GROUP.convertFrom(releaseGroupMapper::fromRecord),
                        ARTIST.convertFrom(artistMapper::fromRecord))
                .from(RELEASE_GROUP)
                .join(ARTIST).on(ARTIST.ARTIST_ID.eq(RELEASE_GROUP.ARTIST_ID))
                .where(afterReleaseGroup(after))
                .orderBy(RELEASE_GROUP.NAME, RELEASE_GROUP.RELEASE_GROUP_ID)
                .limit(size)
                .fetch(group -> summaryMapper.fromPojos(group.value1(), group.value2()));
    }

    private static Condition afterReleaseGroup(final UUID after) {
        if (after == null) {
            return noCondition();
        }
        final var last = RELEASE_GROUP.as("last");
        final var lastKey = select(last.NAME, last.RELEASE_GROUP_ID)
                .from(last)
                .where(last.RELEASE_GROUP_ID.eq(after));
        return row(RELEASE_GROUP.NAME, RELEASE_GROUP.RELEASE_GROUP_ID).gt(lastKey);
    }
}
