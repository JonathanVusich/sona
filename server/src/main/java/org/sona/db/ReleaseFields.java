package org.sona.db;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.sona.db.TrackFields.inLibrary;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.RELEASE_LABEL;
import static org.sona.model.Tables.TRACK;

import java.util.List;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Records;

/**
 * Reads a release's labels and media inside the query of each page that lists them, the release page and the release
 * group page.
 */
@UtilityClass
public final class ReleaseFields {

    /**
     * @param releaseId the release's ID, as a field of the query the labels are read in
     * @return the release's labels by name
     */
    public static Field<List<LabelSummary>> releaseLabels(final Field<UUID> releaseId) {
        final var label = RELEASE_LABEL.label();
        return multiset(select(label.LABEL_ID, label.NAME)
                        .from(RELEASE_LABEL)
                        .where(RELEASE_LABEL.RELEASE_ID.eq(releaseId))
                        .orderBy(label.NAME))
                .convertFrom(labels -> labels.map(Records.mapping(LabelSummary::new)));
    }

    /**
     * @param releaseId the release's ID, as a field of the query the media are read in
     * @return each of the release's media's formats in order, null for a medium whose format MusicBrainz doesn't know
     */
    public static Field<List<String>> releaseFormats(final Field<UUID> releaseId) {
        return multiset(select(MEDIUM.FORMAT)
                        .from(MEDIUM)
                        .where(MEDIUM.RELEASE_ID.eq(releaseId))
                        .orderBy(MEDIUM.POSITION))
                .convertFrom(formats -> formats.map(Record1::value1));
    }

    /**
     * @param releaseId the release's ID, as a field of the query the media are read in
     * @return the release's media in order, each with its tracks in the library in order; a medium may have none, as
     * when only one disc of a release was uploaded
     */
    public static Field<List<MediumWithTracks>> releaseMediaWithTracks(final Field<UUID> releaseId) {
        return multiset(select(MEDIUM.POSITION, MEDIUM.FORMAT, tracks())
                        .from(MEDIUM)
                        .where(MEDIUM.RELEASE_ID.eq(releaseId))
                        .orderBy(MEDIUM.POSITION))
                .convertFrom(media -> media.map(Records.mapping(MediumWithTracks::new)));
    }

    private static Field<List<TrackWithArtist>> tracks() {
        final var artist = TRACK.artist();
        return multiset(select(
                                TRACK.TRACK_ID,
                                TRACK.NAME,
                                TRACK.DURATION,
                                TRACK.POSITION,
                                row(artist.ARTIST_ID, artist.NAME).mapping(TrackArtist::new))
                        .from(TRACK)
                        .where(TRACK.MEDIUM_ID.eq(MEDIUM.MEDIUM_ID))
                        .and(inLibrary(TRACK))
                        .orderBy(TRACK.POSITION))
                .convertFrom(tracks -> tracks.map(Records.mapping(TrackWithArtist::new)));
    }
}
