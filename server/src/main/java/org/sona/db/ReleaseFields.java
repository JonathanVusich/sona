package org.sona.db;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectFrom;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.LABEL;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.RELEASE_LABEL;
import static org.sona.model.Tables.TRACK;
import static org.sona.model.Tables.TRACK_TO_INGEST;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Field;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.LabelMapper;
import org.sona.mappers.MediumMapper;
import org.sona.mappers.MediumWithTracksMapper;
import org.sona.mappers.TrackMapper;
import org.sona.mappers.TrackWithArtistMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.springframework.stereotype.Component;

/**
 * Reads a release's labels and media inside the query of each page that lists them, the release page and the release
 * group page.
 */
@Component
@RequiredArgsConstructor
public final class ReleaseFields {

    private final ArtistMapper artistMapper;
    private final LabelMapper labelMapper;
    private final MediumMapper mediumMapper;
    private final TrackMapper trackMapper;
    private final MediumWithTracksMapper mediumWithTracksMapper;
    private final TrackWithArtistMapper trackWithArtistMapper;

    /**
     * @param releaseId the release's ID, as a field of the query the labels are read in
     * @return the release's labels by name
     */
    public Field<List<Label>> releaseLabels(final Field<UUID> releaseId) {
        final var releaseLabelIds =
                select(RELEASE_LABEL.LABEL_ID).from(RELEASE_LABEL).where(RELEASE_LABEL.RELEASE_ID.eq(releaseId));
        return multiset(selectFrom(LABEL)
                        .where(LABEL.LABEL_ID.in(releaseLabelIds))
                        .orderBy(LABEL.NAME))
                .convertFrom(labels -> labels.map(labelMapper::fromRecord));
    }

    /**
     * @param releaseId the release's ID, as a field of the query the media are read in
     * @return the release's media in order
     */
    public Field<List<Medium>> releaseMedia(final Field<UUID> releaseId) {
        return multiset(selectFrom(MEDIUM)
                        .where(MEDIUM.RELEASE_ID.eq(releaseId))
                        .orderBy(MEDIUM.POSITION))
                .convertFrom(media -> media.map(mediumMapper::fromRecord));
    }

    /**
     * @param releaseId the release's ID, as a field of the query the media are read in
     * @return the release's media in order, each with its tracks in the library in order; a medium may have none, as
     * when only one disc of a release was uploaded
     */
    public Field<List<MediumWithTracks>> releaseMediaWithTracks(final Field<UUID> releaseId) {
        return multiset(select(MEDIUM.convertFrom(mediumMapper::fromRecord), tracks())
                        .from(MEDIUM)
                        .where(MEDIUM.RELEASE_ID.eq(releaseId))
                        .orderBy(MEDIUM.POSITION))
                .convertFrom(media ->
                        media.map(medium -> mediumWithTracksMapper.fromPojos(medium.value1(), medium.value2())));
    }

    /**
     * Only the tracks whose file is in the library: a track's row is stored before its file is moved there, and a move
     * that fails leaves its ingest {@code MOVE_FAILED}.
     */
    private Field<List<TrackWithArtist>> tracks() {
        // Aliased apart from the release's artist, which the outer query joins as ARTIST.
        final var trackArtist = ARTIST.as("track_artist");
        return multiset(select(
                                TRACK.convertFrom(trackMapper::fromRecord),
                                trackArtist.convertFrom(artistMapper::fromRecord))
                        .from(TRACK)
                        .join(trackArtist)
                        .on(trackArtist.ARTIST_ID.eq(TRACK.ARTIST_ID))
                        .join(TRACK_TO_INGEST)
                        .on(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(TRACK.TRACK_INGEST_ID))
                        .where(TRACK.MEDIUM_ID.eq(MEDIUM.MEDIUM_ID))
                        .and(TRACK_TO_INGEST.STATE.eq(IngestState.COMPLETED))
                        .orderBy(TRACK.POSITION))
                .convertFrom(
                        tracks -> tracks.map(track -> trackWithArtistMapper.fromPojos(track.value1(), track.value2())));
    }
}
