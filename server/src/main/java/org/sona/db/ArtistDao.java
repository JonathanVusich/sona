package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.sona.mappers.ArtistMapper;
import org.sona.model.tables.pojos.Artist;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.ARTIST;

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
    public Artist upsert(final Artist artist) {
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

    public Optional<Artist> loadByMusicBrainzId(final UUID musicbrainzArtistId) {
        return dsl.selectFrom(ARTIST)
                .where(ARTIST.MUSICBRAINZ_ARTIST_ID.eq(musicbrainzArtistId))
                .fetchOptional(mapper::fromRecord);
    }

    /**
     * @return the first {@code size} artists, by name
     */
    public List<Artist> loadFirstPage(final int size) {
        return loadPage(noCondition(), size);
    }

    /**
     * Names repeat, so IDs break ties, and the page picks up after the name and ID of the last artist on the page
     * before.
     *
     * @param after the last artist of the page before; an artist who isn't stored has no page after them
     * @return up to {@code size} artists, by name, who come after {@code after}
     */
    public List<Artist> loadNextPage(final UUID after, final int size) {
        return loadPage(afterArtist(after), size);
    }

    private List<Artist> loadPage(final Condition start, final int size) {
        return dsl.selectFrom(ARTIST)
                .where(start)
                .orderBy(ARTIST.NAME, ARTIST.ARTIST_ID)
                .limit(size)
                .fetch(mapper::fromRecord);
    }

    private static Condition afterArtist(final UUID after) {
        final var last = ARTIST.as("last");
        final var lastKey = select(last.NAME, last.ARTIST_ID)
                .from(last)
                .where(last.ARTIST_ID.eq(after));
        return row(ARTIST.NAME, ARTIST.ARTIST_ID).gt(lastKey);
    }
}
