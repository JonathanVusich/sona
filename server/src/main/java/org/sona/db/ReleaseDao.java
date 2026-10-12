package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record4;
import org.jooq.Result;
import org.jooq.SelectOnConditionStep;
import org.sona.mappers.ReleaseDetailsMapper;
import org.sona.mappers.ReleaseMapper;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.records.ArtistRecord;
import org.sona.model.tables.records.LabelRecord;
import org.sona.model.tables.records.MediumRecord;
import org.sona.model.tables.records.ReleaseRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectFrom;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.LABEL;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_LABEL;

@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseDao {

    private final DSLContext dsl;
    private final ReleaseMapper mapper;
    private final ReleaseDetailsMapper detailsMapper;

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
                .set(RELEASE.DATE, excluded(RELEASE.DATE))
                .set(RELEASE.COUNTRY, excluded(RELEASE.COUNTRY))
                .set(RELEASE.RELEASE_GROUP_ID, excluded(RELEASE.RELEASE_GROUP_ID))
                .set(RELEASE.ARTIST_ID, excluded(RELEASE.ARTIST_ID))
                .returning()
                .fetchSingle();
        return mapper.fromRecord(record);
    }

    public Optional<ReleaseDetails> loadDetails(final UUID releaseId) {
        return selectDetails()
                .where(RELEASE.RELEASE_ID.eq(releaseId))
                .fetchOptional(detailsMapper::fromRecord);
    }

    public Optional<Release> loadByMusicBrainzId(final UUID musicbrainzReleaseId) {
        return dsl.selectFrom(RELEASE)
                .where(RELEASE.MUSICBRAINZ_RELEASE_ID.eq(musicbrainzReleaseId))
                .fetchOptional(mapper::fromRecord);
    }

    public List<Release> loadByIds(final Collection<UUID> releaseIds) {
        return dsl.selectFrom(RELEASE)
                .where(RELEASE.RELEASE_ID.in(releaseIds))
                .fetch(mapper::fromRecord);
    }

    /**
     * @return the release group's releases with their details, the earliest first, which stands for the group
     */
    public List<ReleaseDetails> loadDetailsByReleaseGroup(final UUID releaseGroupId) {
        return selectDetails()
                .where(RELEASE.RELEASE_GROUP_ID.eq(releaseGroupId))
                .orderBy(RELEASE.DATE.asc().nullsLast(), RELEASE.RELEASE_ID)
                .fetch(detailsMapper::fromRecord);
    }

    /**
     * @return the ID of each release group's earliest release, which stands for the group
     */
    public List<UUID> loadMainReleaseIds(final Collection<UUID> releaseGroupIds) {
        return dsl.select(RELEASE.RELEASE_ID)
                .distinctOn(RELEASE.RELEASE_GROUP_ID)
                .from(RELEASE)
                .where(RELEASE.RELEASE_GROUP_ID.in(releaseGroupIds))
                .orderBy(RELEASE.RELEASE_GROUP_ID, RELEASE.DATE.asc().nullsLast(), RELEASE.RELEASE_ID)
                .fetch(RELEASE.RELEASE_ID);
    }

    private SelectOnConditionStep<Record4<ReleaseRecord, ArtistRecord, Result<LabelRecord>, Result<MediumRecord>>>
    selectDetails() {
        return dsl.select(RELEASE, ARTIST, labels(), media())
                .from(RELEASE)
                .leftJoin(ARTIST).on(ARTIST.ARTIST_ID.eq(RELEASE.ARTIST_ID));
    }

    private static Field<Result<LabelRecord>> labels() {
        final var releaseLabelIds = select(RELEASE_LABEL.LABEL_ID)
                .from(RELEASE_LABEL)
                .where(RELEASE_LABEL.RELEASE_ID.eq(RELEASE.RELEASE_ID));
        return multiset(selectFrom(LABEL).where(LABEL.LABEL_ID.in(releaseLabelIds)).orderBy(LABEL.NAME));
    }

    private static Field<Result<MediumRecord>> media() {
        return multiset(selectFrom(MEDIUM).where(MEDIUM.RELEASE_ID.eq(RELEASE.RELEASE_ID)).orderBy(MEDIUM.POSITION));
    }
}
