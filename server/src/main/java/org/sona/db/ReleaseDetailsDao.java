package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Result;
import org.sona.mappers.ReleaseDetailsMapper;
import org.sona.model.tables.records.ArtistRecord;
import org.sona.model.tables.records.LabelRecord;
import org.sona.model.tables.records.MediumRecord;
import org.sona.model.tables.records.TrackRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
import static org.sona.model.Tables.TRACK;

@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseDetailsDao {

    private final DSLContext dsl;
    private final ReleaseDetailsMapper mapper;

    public Optional<ReleaseDetails> load(final UUID releaseId) {
        return dsl.select(RELEASE, ARTIST, labels(), media(), tracks())
                .from(RELEASE)
                .join(ARTIST).on(ARTIST.ARTIST_ID.eq(RELEASE.ARTIST_ID))
                .where(RELEASE.RELEASE_ID.eq(releaseId))
                .fetchOptional(mapper::fromRecord);
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

    private static Field<Result<Record2<TrackRecord, ArtistRecord>>> tracks() {
        // Aliased apart from the release's artist, which the outer query joins as ARTIST.
        final var trackArtist = ARTIST.as("track_artist");
        return multiset(select(TRACK, trackArtist)
                .from(TRACK)
                .join(trackArtist).on(trackArtist.ARTIST_ID.eq(TRACK.ARTIST_ID))
                .where(TRACK.RELEASE_ID.eq(RELEASE.RELEASE_ID))
                .orderBy(TRACK.DISC_NUMBER, TRACK.TRACK_NUMBER));
    }
}
