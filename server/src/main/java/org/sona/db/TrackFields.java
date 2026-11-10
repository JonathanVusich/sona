package org.sona.db;

import static org.jooq.impl.DSL.row;

import lombok.experimental.UtilityClass;
import org.jooq.Condition;
import org.jooq.SelectField;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.Track;

/**
 * The parts of queries that read tracks. Each takes the track as the query reaches it, as {@code TRACK} or along a
 * path ({@code PLAY.track()}), and joins the track's own rows along their foreign keys.
 */
@UtilityClass
public final class TrackFields {

    /**
     * A track is in the library once its ingest completes: its row is stored before its file is moved there, and a
     * move that fails leaves its ingest {@code MOVE_FAILED}.
     */
    public static Condition inLibrary(final Track track) {
        return track.trackToIngest().STATE.eq(IngestState.COMPLETED);
    }

    public static SelectField<TrackSummary> trackSummary(final Track track) {
        final var artist = track.artist();
        final var release = track.medium().release();
        return row(
                        track.TRACK_ID,
                        track.NAME,
                        track.DURATION,
                        track.POSITION,
                        row(artist.ARTIST_ID, artist.NAME).mapping(TrackArtist::new),
                        row(release.RELEASE_ID, release.NAME).mapping(TrackRelease::new))
                .mapping(TrackSummary::new);
    }
}
