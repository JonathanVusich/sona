package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record4;
import org.jooq.Result;
import org.jooq.SelectOnConditionStep;
import org.sona.mappers.ReleaseDetailsMapper;
import org.sona.model.tables.records.ArtistRecord;
import org.sona.model.tables.records.LabelRecord;
import org.sona.model.tables.records.MediumRecord;
import org.sona.model.tables.records.ReleaseRecord;
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
import static org.sona.model.Tables.RELEASE_LABEL;

@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseDetailsDao {

    private final DSLContext dsl;
    private final ReleaseDetailsMapper mapper;

    public Optional<ReleaseDetails> load(final UUID releaseId) {
        return selectDetails()
                .where(RELEASE.RELEASE_ID.eq(releaseId))
                .fetchOptional(mapper::fromRecord);
    }

    /**
     * @return the release group's releases with their details, the earliest first, which stands for the group
     */
    public List<ReleaseDetails> loadByReleaseGroup(final UUID releaseGroupId) {
        return selectDetails()
                .where(RELEASE.RELEASE_GROUP_ID.eq(releaseGroupId))
                .orderBy(RELEASE.DATE.asc().nullsLast(), RELEASE.RELEASE_ID)
                .fetch(mapper::fromRecord);
    }

    private SelectOnConditionStep<Record4<ReleaseRecord, ArtistRecord, Result<LabelRecord>, Result<MediumRecord>>>
    selectDetails() {
        return dsl.select(RELEASE, ARTIST, labels(), media())
                .from(RELEASE)
                .join(ARTIST).on(ARTIST.ARTIST_ID.eq(RELEASE.ARTIST_ID));
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
