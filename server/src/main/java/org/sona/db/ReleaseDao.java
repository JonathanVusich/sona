package org.sona.db;

import static java.util.Comparator.comparingInt;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.selectFrom;
import static org.sona.db.ReleaseFields.releaseLabels;
import static org.sona.db.ReleaseFields.releaseMediaWithTracks;
import static org.sona.model.Tables.LABEL;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_LABEL;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.sona.mappers.LabelMapper;
import org.sona.mappers.MediumMapper;
import org.sona.mappers.ReleaseLabelMapper;
import org.sona.mappers.ReleaseMapper;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases with their labels and media, and the release page. A release is stored with its labels and media in one
 * transaction, so a stored release always has them.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseDao {

    private final DSLContext dsl;
    private final ReleaseMapper releaseMapper;
    private final LabelMapper labelMapper;
    private final ReleaseLabelMapper releaseLabelMapper;
    private final MediumMapper mediumMapper;

    /**
     * Inserts the release with its labels and media, or overwrites the stored one with the same MusicBrainz ID, and
     * the labels and media already stored, with their newer data. Labels are found by their MusicBrainz IDs, and
     * media by their positions on the release.
     *
     * @param media the release's media, whose release ID is replaced by the stored release's
     * @return the stored release, which keeps its original ID if it already existed, with its media in order
     */
    public ReleaseWithMedia upsert(final Release release, final List<Label> labels, final List<Medium> media) {
        final var stored = dsl.insertInto(RELEASE)
                .set(releaseMapper.toRecord(release))
                .onConflict(RELEASE.MUSICBRAINZ_RELEASE_ID)
                .doUpdate()
                .set(RELEASE.NAME, excluded(RELEASE.NAME))
                .set(RELEASE.DATE, excluded(RELEASE.DATE))
                .set(RELEASE.COUNTRY, excluded(RELEASE.COUNTRY))
                .set(RELEASE.RELEASE_GROUP_ID, excluded(RELEASE.RELEASE_GROUP_ID))
                .set(RELEASE.ARTIST_ID, excluded(RELEASE.ARTIST_ID))
                .returning()
                .fetchSingle(releaseMapper::fromRecord);
        upsertLabels(stored.releaseId(), labels);
        return new ReleaseWithMedia(stored, upsertMedia(stored.releaseId(), media));
    }

    public Optional<ReleaseWithMedia> loadByMusicBrainzId(final UUID musicbrainzReleaseId) {
        final var media = multiset(selectFrom(MEDIUM)
                        .where(MEDIUM.RELEASE_ID.eq(RELEASE.RELEASE_ID))
                        .orderBy(MEDIUM.POSITION))
                .convertFrom(rows -> rows.map(mediumMapper::fromRecord));
        return dsl.select(RELEASE.convertFrom(releaseMapper::fromRecord), media)
                .from(RELEASE)
                .where(RELEASE.MUSICBRAINZ_RELEASE_ID.eq(musicbrainzReleaseId))
                .fetchOptional(Records.mapping(ReleaseWithMedia::new));
    }

    /**
     * @return the release page's data, if the release is in the library: a release is only once one of its tracks is
     */
    public Optional<ReleaseDetails> loadDetails(final UUID releaseId) {
        final var artist = RELEASE.artist();
        return dsl.select(
                        RELEASE.RELEASE_ID,
                        RELEASE.NAME,
                        RELEASE.DATE,
                        RELEASE.COUNTRY,
                        RELEASE.TRACK_COUNT,
                        RELEASE.RELEASE_GROUP_ID,
                        artist.ARTIST_ID,
                        artist.NAME,
                        releaseLabels(RELEASE.RELEASE_ID),
                        releaseMediaWithTracks(RELEASE.RELEASE_ID))
                .from(RELEASE)
                .where(RELEASE.RELEASE_ID.eq(releaseId))
                .and(RELEASE.TRACK_COUNT.gt(0))
                .fetchOptional(Records.mapping(ReleaseDetails::new));
    }

    private void upsertLabels(final UUID releaseId, final List<Label> labels) {
        if (labels.isEmpty()) {
            return;
        }
        final var records = labels.stream().map(labelMapper::toRecord).toList();
        final var labelIds = dsl.insertInto(LABEL)
                .columns(LABEL.fields())
                .valuesOfRecords(records)
                .onConflict(LABEL.MUSICBRAINZ_LABEL_ID)
                .doUpdate()
                .set(LABEL.NAME, excluded(LABEL.NAME))
                .returning(LABEL.LABEL_ID)
                .fetch(LABEL.LABEL_ID);
        final var releaseLabels = labelIds.stream()
                .map(labelId -> releaseLabelMapper.toRecord(new ReleaseLabel(releaseId, labelId)))
                .toList();
        dsl.insertInto(RELEASE_LABEL)
                .columns(RELEASE_LABEL.fields())
                .valuesOfRecords(releaseLabels)
                .onConflictDoNothing()
                .execute();
    }

    /**
     * @return the stored media in order
     */
    private List<Medium> upsertMedia(final UUID releaseId, final List<Medium> media) {
        if (media.isEmpty()) {
            return List.of();
        }
        final var records = media.stream()
                .map(medium -> mediumMapper.toRecord(
                        new Medium(medium.mediumId(), releaseId, medium.position(), medium.format())))
                .toList();
        final var stored = dsl.insertInto(MEDIUM)
                .columns(MEDIUM.fields())
                .valuesOfRecords(records)
                .onConflict(MEDIUM.RELEASE_ID, MEDIUM.POSITION)
                .doUpdate()
                .set(MEDIUM.FORMAT, excluded(MEDIUM.FORMAT))
                .returning()
                .fetch(mediumMapper::fromRecord);
        return stored.stream().sorted(comparingInt(Medium::position)).toList();
    }
}
