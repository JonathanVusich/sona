package org.sona.db;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.PLAYLIST;
import static org.sona.model.Tables.PLAYLIST_TRACK;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.TRACK;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.PlaylistDetailsMapper;
import org.sona.mappers.PlaylistEntryMapper;
import org.sona.mappers.PlaylistMapper;
import org.sona.mappers.ReleaseMapper;
import org.sona.mappers.TrackMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every table the query reads is converted to its pojo by its table's mapper as it's read, so the composite mappers
 * build their results from pojos only.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PlaylistDetailsDao {

    private final DSLContext dsl;
    private final PlaylistMapper playlistMapper;
    private final TrackMapper trackMapper;
    private final ArtistMapper artistMapper;
    private final ReleaseMapper releaseMapper;
    private final PlaylistEntryMapper entryMapper;
    private final PlaylistDetailsMapper detailsMapper;

    /**
     * @return the playlist with every track on it in order, if the user has it
     */
    public Optional<PlaylistDetails> loadById(final UUID userId, final UUID playlistId) {
        return dsl.select(PLAYLIST.convertFrom(playlistMapper::fromRecord), entries())
                .from(PLAYLIST)
                .where(PLAYLIST.PLAYLIST_ID.eq(playlistId))
                .and(PLAYLIST.USER_ID.eq(userId))
                .fetchOptional(playlist -> detailsMapper.fromPojos(playlist.value1(), playlist.value2()));
    }

    private Field<List<PlaylistEntry>> entries() {
        return multiset(select(
                                TRACK.convertFrom(trackMapper::fromRecord),
                                ARTIST.convertFrom(artistMapper::fromRecord),
                                RELEASE.convertFrom(releaseMapper::fromRecord))
                        .from(PLAYLIST_TRACK)
                        .join(TRACK)
                        .on(TRACK.TRACK_ID.eq(PLAYLIST_TRACK.TRACK_ID))
                        .join(ARTIST)
                        .on(ARTIST.ARTIST_ID.eq(TRACK.ARTIST_ID))
                        .join(MEDIUM)
                        .on(MEDIUM.MEDIUM_ID.eq(TRACK.MEDIUM_ID))
                        .join(RELEASE)
                        .on(RELEASE.RELEASE_ID.eq(MEDIUM.RELEASE_ID))
                        .where(PLAYLIST_TRACK.PLAYLIST_ID.eq(PLAYLIST.PLAYLIST_ID))
                        .orderBy(PLAYLIST_TRACK.POSITION))
                .convertFrom(entries ->
                        entries.map(entry -> entryMapper.fromPojos(entry.value1(), entry.value2(), entry.value3())));
    }
}
