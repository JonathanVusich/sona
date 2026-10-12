package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.SelectOnConditionStep;
import org.sona.mappers.ReleaseGroupWithArtistMapper;
import org.sona.model.tables.records.ArtistRecord;
import org.sona.model.tables.records.ReleaseGroupRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.RELEASE_GROUP;

@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseGroupWithArtistDao {

    private final DSLContext dsl;
    private final ReleaseGroupWithArtistMapper mapper;

    public Optional<ReleaseGroupWithArtist> load(final UUID releaseGroupId) {
        return selectWithArtist()
                .where(RELEASE_GROUP.RELEASE_GROUP_ID.eq(releaseGroupId))
                .fetchOptional(mapper::fromRecord);
    }

    /**
     * @param after the last release group of the previous page, or null for the first page
     * @return a page of release groups with their artists, by name
     */
    public List<ReleaseGroupWithArtist> loadByName(final UUID after, final int limit) {
        return selectWithArtist()
                .where(afterByName(after))
                .orderBy(RELEASE_GROUP.NAME, RELEASE_GROUP.RELEASE_GROUP_ID)
                .limit(limit)
                .fetch(mapper::fromRecord);
    }

    /**
     * IDs are UUIDv7s, so they sort by when the release group was added.
     *
     * @param after the last release group of the previous page, or null for the first page
     * @return a page of release groups with their artists, the most recently added first
     */
    public List<ReleaseGroupWithArtist> loadNewest(final UUID after, final int limit) {
        return selectWithArtist()
                .where(addedBefore(after))
                .orderBy(RELEASE_GROUP.RELEASE_GROUP_ID.desc())
                .limit(limit)
                .fetch(mapper::fromRecord);
    }

    private SelectOnConditionStep<Record2<ReleaseGroupRecord, ArtistRecord>> selectWithArtist() {
        return dsl.select(RELEASE_GROUP, ARTIST)
                .from(RELEASE_GROUP)
                .join(ARTIST).on(ARTIST.ARTIST_ID.eq(RELEASE_GROUP.ARTIST_ID));
    }

    private static Condition afterByName(final UUID after) {
        if (after == null) {
            return noCondition();
        }
        final var previous = RELEASE_GROUP.as("previous");
        return row(RELEASE_GROUP.NAME, RELEASE_GROUP.RELEASE_GROUP_ID).gt(
                select(previous.NAME, previous.RELEASE_GROUP_ID)
                        .from(previous)
                        .where(previous.RELEASE_GROUP_ID.eq(after)));
    }

    private static Condition addedBefore(final UUID after) {
        if (after == null) {
            return noCondition();
        }
        return RELEASE_GROUP.RELEASE_GROUP_ID.lt(after);
    }
}
