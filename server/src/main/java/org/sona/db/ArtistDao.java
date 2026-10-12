package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.sona.mappers.ArtistMapper;
import org.sona.model.tables.pojos.Artist;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.RELEASE_GROUP;

@Component
@RequiredArgsConstructor
@Transactional
public class ArtistDao {

    private final DSLContext dsl;
    private final ArtistMapper mapper;

    /**
     * Inserts the artist, or overwrites the stored one with the same MusicBrainz ID with its newer data.
     *
     * @return the stored artist, which keeps its original ID if it already existed
     */
    public Artist upsert(Artist artist) {
        final var record = dsl.insertInto(ARTIST)
                .set(mapper.toRecord(artist))
                .onConflict(ARTIST.MUSICBRAINZ_ARTIST_ID)
                .doUpdate()
                .set(ARTIST.NAME, excluded(ARTIST.NAME))
                .set(ARTIST.COUNTRY, excluded(ARTIST.COUNTRY))
                .returning()
                .fetchSingle();
        return mapper.fromRecord(record);
    }

    public Optional<Artist> loadById(final UUID artistId) {
        return dsl.selectFrom(ARTIST)
                .where(ARTIST.ARTIST_ID.eq(artistId))
                .fetchOptional(mapper::fromRecord);
    }

    public Optional<Artist> loadByMusicBrainzId(final UUID musicbrainzArtistId) {
        return dsl.selectFrom(ARTIST)
                .where(ARTIST.MUSICBRAINZ_ARTIST_ID.eq(musicbrainzArtistId))
                .fetchOptional(mapper::fromRecord);
    }

    public List<Artist> loadByIds(final Collection<UUID> artistIds) {
        return dsl.selectFrom(ARTIST)
                .where(ARTIST.ARTIST_ID.in(artistIds))
                .fetch(mapper::fromRecord);
    }

    /**
     * Artists only credited on tracks or releases are left out, as they have no release groups to browse.
     *
     * @param after the last artist of the previous page, or null for the first page
     * @return a page of the artists with release groups, by name
     */
    public List<Artist> loadReleaseArtists(final UUID after, final int limit) {
        return dsl.selectFrom(ARTIST)
                .where(exists(selectOne().from(RELEASE_GROUP).where(RELEASE_GROUP.ARTIST_ID.eq(ARTIST.ARTIST_ID))))
                .and(afterByName(after))
                .orderBy(ARTIST.NAME, ARTIST.ARTIST_ID)
                .limit(limit)
                .fetch(mapper::fromRecord);
    }

    private static Condition afterByName(final UUID after) {
        if (after == null) {
            return noCondition();
        }
        final var previous = ARTIST.as("previous");
        return row(ARTIST.NAME, ARTIST.ARTIST_ID)
                .gt(select(previous.NAME, previous.ARTIST_ID).from(previous).where(previous.ARTIST_ID.eq(after)));
    }
}
