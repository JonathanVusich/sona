package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.Field;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.MediumMapper;
import org.sona.mappers.MediumWithTracksMapper;
import org.sona.mappers.TrackMapper;
import org.sona.mappers.TrackWithArtistMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Medium;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectFrom;
import static org.jooq.impl.DSL.selectOne;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.TRACK;
import static org.sona.model.Tables.TRACK_TO_INGEST;

/**
 * Reads a release's media inside the query of each page that lists them, the release page and the release group page.
 */
@Component
@RequiredArgsConstructor
public final class MediaFields {

    private final ArtistMapper artistMapper;
    private final MediumMapper mediumMapper;
    private final TrackMapper trackMapper;
    private final MediumWithTracksMapper mediumWithTracksMapper;
    private final TrackWithArtistMapper trackWithArtistMapper;

    /**
     * @param releaseId the release's ID, as a field of the query the media are read in
     * @return the release's media with a track in the library, in order, each with those tracks in order; a medium
     * none of whose tracks are in the library, as when only one disc of a release was uploaded, is left out
     */
    public Field<List<MediumWithTracks>> releaseMedia(final Field<UUID> releaseId) {
        final var tracksInLibrary = selectOne()
                .from(TRACK)
                .join(TRACK_TO_INGEST).on(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(TRACK.TRACK_INGEST_ID))
                .where(TRACK.MEDIUM_ID.eq(MEDIUM.MEDIUM_ID))
                .and(TRACK_TO_INGEST.STATE.eq(IngestState.COMPLETED));
        return multiset(select(MEDIUM.convertFrom(mediumMapper::fromRecord), tracks())
                .from(MEDIUM)
                .where(MEDIUM.RELEASE_ID.eq(releaseId))
                .and(exists(tracksInLibrary))
                .orderBy(MEDIUM.POSITION))
                .convertFrom(media -> media.map(medium -> mediumWithTracksMapper.fromPojos(medium.value1(),
                        medium.value2())));
    }

    /**
     * @param releaseId the release's ID, as a field of the query the media are read in
     * @return every medium of the release in order, whether or not any of its tracks is in the library, as the
     * release's formats describe the whole release
     */
    public Field<List<Medium>> allReleaseMedia(final Field<UUID> releaseId) {
        return multiset(selectFrom(MEDIUM).where(MEDIUM.RELEASE_ID.eq(releaseId)).orderBy(MEDIUM.POSITION))
                .convertFrom(media -> media.map(mediumMapper::fromRecord));
    }

    /**
     * Only the tracks whose file is in the library: a track's row is stored before its file is moved there, and a move
     * that fails leaves its ingest {@code MOVE_FAILED}.
     */
    private Field<List<TrackWithArtist>> tracks() {
        // Aliased apart from the release's artist, which the outer query joins as ARTIST.
        final var trackArtist = ARTIST.as("track_artist");
        return multiset(select(TRACK.convertFrom(trackMapper::fromRecord),
                        trackArtist.convertFrom(artistMapper::fromRecord))
                .from(TRACK)
                .join(trackArtist).on(trackArtist.ARTIST_ID.eq(TRACK.ARTIST_ID))
                .join(TRACK_TO_INGEST).on(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(TRACK.TRACK_INGEST_ID))
                .where(TRACK.MEDIUM_ID.eq(MEDIUM.MEDIUM_ID))
                .and(TRACK_TO_INGEST.STATE.eq(IngestState.COMPLETED))
                .orderBy(TRACK.POSITION))
                .convertFrom(tracks -> tracks.map(track -> trackWithArtistMapper.fromPojos(track.value1(),
                        track.value2())));
    }
}
