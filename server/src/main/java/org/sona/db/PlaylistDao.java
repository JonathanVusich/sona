package org.sona.db;

import static org.sona.model.Tables.PLAYLIST;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.PlaylistMapper;
import org.sona.model.tables.pojos.Playlist;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each user sees only their own playlists, so every method that finds one takes the user, and finds nothing for a
 * playlist of someone else's.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PlaylistDao {

    private final DSLContext dsl;
    private final PlaylistMapper mapper;

    public void insert(final Playlist playlist) {
        dsl.insertInto(PLAYLIST).set(mapper.toRecord(playlist)).execute();
    }

    /**
     * @param description null for none
     * @return the playlist as it is now, if the user has it
     */
    public Optional<Playlist> updateDetails(
            final UUID userId, final UUID playlistId, final String name, final String description) {
        return dsl.update(PLAYLIST)
                .set(PLAYLIST.NAME, name)
                .set(PLAYLIST.DESCRIPTION, description)
                .where(PLAYLIST.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST.USER_ID.eq(userId))
                .returning()
                .fetchOptional(mapper::fromRecord);
    }

    /**
     * Deletes the playlist with its list of tracks, which stay in the library.
     *
     * @return the playlist deleted, if the user had it
     */
    public Optional<Playlist> delete(final UUID userId, final UUID playlistId) {
        return dsl.deleteFrom(PLAYLIST)
                .where(PLAYLIST.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST.USER_ID.eq(userId))
                .returning()
                .fetchOptional(mapper::fromRecord);
    }
}
