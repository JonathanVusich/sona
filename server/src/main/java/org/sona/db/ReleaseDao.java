package org.sona.db;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.LABEL;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_LABEL;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.LabelMapper;
import org.sona.mappers.MediumMapper;
import org.sona.mappers.ReleaseDetailsMapper;
import org.sona.mappers.ReleaseLabelMapper;
import org.sona.mappers.ReleaseMapper;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases with their labels and media, and the release page. Every table a composite read selects is converted to
 * its pojo by its table's mapper as it's read, so the composite mappers build their results from pojos only.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseDao {

    private final DSLContext dsl;
    private final ReleaseMapper releaseMapper;
    private final ArtistMapper artistMapper;
    private final LabelMapper labelMapper;
    private final ReleaseLabelMapper releaseLabelMapper;
    private final MediumMapper mediumMapper;
    private final ReleaseDetailsMapper detailsMapper;
    private final ReleaseFields releaseFields;

    /**
     * Inserts the release, or overwrites the stored one with the same MusicBrainz ID with its newer data.
     *
     * @return the stored release, which keeps its original ID if it already existed
     */
    public Release upsert(final Release release) {
        final var record = dsl.insertInto(RELEASE)
                .set(releaseMapper.toRecord(release))
                .onConflict(RELEASE.MUSICBRAINZ_RELEASE_ID)
                .doUpdate()
                .set(RELEASE.NAME, excluded(RELEASE.NAME))
                .set(RELEASE.DATE, excluded(RELEASE.DATE))
                .set(RELEASE.COUNTRY, excluded(RELEASE.COUNTRY))
                .set(RELEASE.RELEASE_GROUP_ID, excluded(RELEASE.RELEASE_GROUP_ID))
                .set(RELEASE.ARTIST_ID, excluded(RELEASE.ARTIST_ID))
                .returning()
                .fetchSingle();
        return releaseMapper.fromRecord(record);
    }

    public Optional<Release> loadByMusicBrainzId(final UUID musicbrainzReleaseId) {
        return dsl.selectFrom(RELEASE)
                .where(RELEASE.MUSICBRAINZ_RELEASE_ID.eq(musicbrainzReleaseId))
                .fetchOptional(releaseMapper::fromRecord);
    }

    /**
     * @return the release page's data, if the release is in the library: a release is only once one of its tracks is
     */
    public Optional<ReleaseDetails> loadDetails(final UUID releaseId) {
        return dsl.select(
                        RELEASE.convertFrom(releaseMapper::fromRecord),
                        ARTIST.convertFrom(artistMapper::fromRecord),
                        releaseFields.releaseLabels(RELEASE.RELEASE_ID),
                        releaseFields.releaseMediaWithTracks(RELEASE.RELEASE_ID))
                .from(RELEASE)
                .join(ARTIST)
                .on(ARTIST.ARTIST_ID.eq(RELEASE.ARTIST_ID))
                .where(RELEASE.RELEASE_ID.eq(releaseId))
                .and(RELEASE.TRACK_COUNT.gt(0))
                .fetchOptional(release -> detailsMapper.fromPojos(
                        release.value1(), release.value2(), release.value3(), release.value4()));
    }

    /**
     * Inserts the labels in one statement, overwriting stored ones with the same MusicBrainz ID with their newer data.
     *
     * @return the stored labels, which keep their original IDs if they already existed, in no particular order
     */
    public List<Label> upsertLabels(final List<Label> labels) {
        if (labels.isEmpty()) {
            return List.of();
        }
        final var records = labels.stream().map(labelMapper::toRecord).toList();
        return dsl.insertInto(LABEL)
                .columns(LABEL.fields())
                .valuesOfRecords(records)
                .onConflict(LABEL.MUSICBRAINZ_LABEL_ID)
                .doUpdate()
                .set(LABEL.NAME, excluded(LABEL.NAME))
                .returning()
                .fetch(labelMapper::fromRecord);
    }

    /**
     * Records in one statement which labels releases came out on, skipping those already stored.
     */
    public void upsertReleaseLabels(final List<ReleaseLabel> releaseLabels) {
        if (releaseLabels.isEmpty()) {
            return;
        }
        final var records =
                releaseLabels.stream().map(releaseLabelMapper::toRecord).toList();
        dsl.insertInto(RELEASE_LABEL)
                .columns(RELEASE_LABEL.fields())
                .valuesOfRecords(records)
                .onConflictDoNothing()
                .execute();
    }

    /**
     * Inserts the media in one statement, overwriting stored ones at the same position of their release with their
     * newer data.
     *
     * @return the stored media, which keep their original IDs if they already existed, in no particular order
     */
    public List<Medium> upsertMedia(final List<Medium> media) {
        if (media.isEmpty()) {
            return List.of();
        }
        final var records = media.stream().map(mediumMapper::toRecord).toList();
        return dsl.insertInto(MEDIUM)
                .columns(MEDIUM.fields())
                .valuesOfRecords(records)
                .onConflict(MEDIUM.RELEASE_ID, MEDIUM.POSITION)
                .doUpdate()
                .set(MEDIUM.FORMAT, excluded(MEDIUM.FORMAT))
                .returning()
                .fetch(mediumMapper::fromRecord);
    }

    public Optional<Medium> loadMediumByPosition(final UUID releaseId, final int position) {
        return dsl.selectFrom(MEDIUM)
                .where(MEDIUM.RELEASE_ID.eq(releaseId))
                .and(MEDIUM.POSITION.eq(position))
                .fetchOptional(mediumMapper::fromRecord);
    }
}
