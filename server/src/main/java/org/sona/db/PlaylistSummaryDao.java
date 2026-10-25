package org.sona.db;

import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.PLAYLIST;
import static org.sona.model.Tables.PLAYLIST_TRACK;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_GROUP;
import static org.sona.model.Tables.TRACK;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.sona.mappers.PlaylistMapper;
import org.sona.mappers.PlaylistSummaryMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists a user's playlists.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PlaylistSummaryDao {

    private static final int COVER_SIZE = 4;
    // The release groups are picked from the playlist's first tracks only, so a long playlist isn't read whole.
    private static final int COVER_TRACKS = 100;

    private final DSLContext dsl;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSummaryMapper summaryMapper;

    /**
     * @return the user's playlists by name, each with what its cover shows
     */
    public List<PlaylistSummary> loadByUser(final UUID userId) {
        return dsl.select(PLAYLIST.convertFrom(playlistMapper::fromRecord), coverNames())
                .from(PLAYLIST)
                .where(PLAYLIST.USER_ID.eq(userId))
                .orderBy(PLAYLIST.NAME, PLAYLIST.PLAYLIST_ID)
                .fetch(playlist -> summaryMapper.fromPojos(playlist.value1(), playlist.value2()));
    }

    /**
     * @return the names of the release groups the playlist's first tracks are from, in the order they first appear
     */
    private static Field<List<String>> coverNames() {
        final var firstTracks = select(PLAYLIST_TRACK.TRACK_ID, PLAYLIST_TRACK.POSITION)
                .from(PLAYLIST_TRACK)
                .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(PLAYLIST.PLAYLIST_ID))
                .orderBy(PLAYLIST_TRACK.POSITION)
                .limit(COVER_TRACKS)
                .asTable("first_tracks");
        final var trackId = firstTracks.field(PLAYLIST_TRACK.TRACK_ID);
        final var position = firstTracks.field(PLAYLIST_TRACK.POSITION);
        return multiset(select(RELEASE_GROUP.NAME)
                        .from(firstTracks)
                        .join(TRACK)
                        .on(TRACK.TRACK_ID.eq(trackId))
                        .join(MEDIUM)
                        .on(MEDIUM.MEDIUM_ID.eq(TRACK.MEDIUM_ID))
                        .join(RELEASE)
                        .on(RELEASE.RELEASE_ID.eq(MEDIUM.RELEASE_ID))
                        .join(RELEASE_GROUP)
                        .on(RELEASE_GROUP.RELEASE_GROUP_ID.eq(RELEASE.RELEASE_GROUP_ID))
                        .groupBy(RELEASE_GROUP.RELEASE_GROUP_ID, RELEASE_GROUP.NAME)
                        .orderBy(min(position))
                        .limit(COVER_SIZE))
                .convertFrom(names -> names.map(Record1::value1));
    }
}
