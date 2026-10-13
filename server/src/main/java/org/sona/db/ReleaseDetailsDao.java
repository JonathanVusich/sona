package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.sona.mappers.LabelMapper;
import org.sona.mappers.MediumWithTracksMapper;
import org.sona.mappers.ReleaseDetailsMapper;
import org.sona.mappers.TrackWithArtistMapper;
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
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_LABEL;
import static org.sona.model.Tables.TRACK;

/**
 * Each multiset is mapped to pojos as it's read, so the mappers take plain records and lists.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseDetailsDao {

    private final DSLContext dsl;
    private final ReleaseDetailsMapper mapper;
    private final LabelMapper labelMapper;
    private final MediumWithTracksMapper mediumMapper;
    private final TrackWithArtistMapper trackMapper;

    public Optional<ReleaseDetails> loadById(final UUID releaseId) {
        return dsl.select(RELEASE, ARTIST, labels(), media())
                .from(RELEASE)
                .join(ARTIST).on(ARTIST.ARTIST_ID.eq(RELEASE.ARTIST_ID))
                .where(RELEASE.RELEASE_ID.eq(releaseId))
                .fetchOptional(release -> mapper.fromRecords(release.value1(), release.value2(), release.value3(),
                        release.value4()));
    }

    private Field<List<Label>> labels() {
        final var releaseLabelIds = select(RELEASE_LABEL.LABEL_ID)
                .from(RELEASE_LABEL)
                .where(RELEASE_LABEL.RELEASE_ID.eq(RELEASE.RELEASE_ID));
        return multiset(selectFrom(LABEL).where(LABEL.LABEL_ID.in(releaseLabelIds)).orderBy(LABEL.NAME))
                .convertFrom(labels -> labels.map(labelMapper::fromRecord));
    }

    private Field<List<MediumWithTracks>> media() {
        return multiset(select(MEDIUM, tracks())
                .from(MEDIUM)
                .where(MEDIUM.RELEASE_ID.eq(RELEASE.RELEASE_ID))
                .orderBy(MEDIUM.POSITION))
                .convertFrom(media -> media.map(medium -> mediumMapper.fromRecords(medium.value1(),
                        medium.value2())));
    }

    private Field<List<TrackWithArtist>> tracks() {
        // Aliased apart from the release's artist, which the outer query joins as ARTIST.
        final var trackArtist = ARTIST.as("track_artist");
        return multiset(select(TRACK, trackArtist)
                .from(TRACK)
                .join(trackArtist).on(trackArtist.ARTIST_ID.eq(TRACK.ARTIST_ID))
                .where(TRACK.MEDIUM_ID.eq(MEDIUM.MEDIUM_ID))
                .orderBy(TRACK.POSITION))
                .convertFrom(tracks -> tracks.map(track -> trackMapper.fromRecords(track.value1(), track.value2())));
    }
}
