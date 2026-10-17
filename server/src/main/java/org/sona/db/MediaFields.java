package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.Field;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.MediumMapper;
import org.sona.mappers.MediumWithTracksMapper;
import org.sona.mappers.TrackMapper;
import org.sona.mappers.TrackWithArtistMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.TRACK;

/**
 * Reads a release's media with their tracks inside the query of each page that lists them, the release page and the
 * release group page.
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
     * @return the release's media in order, each with its tracks in order
     */
    public Field<List<MediumWithTracks>> releaseMedia(final Field<UUID> releaseId) {
        return multiset(select(MEDIUM.convertFrom(mediumMapper::fromRecord), tracks())
                .from(MEDIUM)
                .where(MEDIUM.RELEASE_ID.eq(releaseId))
                .orderBy(MEDIUM.POSITION))
                .convertFrom(media -> media.map(medium -> mediumWithTracksMapper.fromPojos(medium.value1(),
                        medium.value2())));
    }

    private Field<List<TrackWithArtist>> tracks() {
        // Aliased apart from the release's artist, which the outer query joins as ARTIST.
        final var trackArtist = ARTIST.as("track_artist");
        return multiset(select(TRACK.convertFrom(trackMapper::fromRecord),
                        trackArtist.convertFrom(artistMapper::fromRecord))
                .from(TRACK)
                .join(trackArtist).on(trackArtist.ARTIST_ID.eq(TRACK.ARTIST_ID))
                .where(TRACK.MEDIUM_ID.eq(MEDIUM.MEDIUM_ID))
                .orderBy(TRACK.POSITION))
                .convertFrom(tracks -> tracks.map(track -> trackWithArtistMapper.fromPojos(track.value1(),
                        track.value2())));
    }
}
