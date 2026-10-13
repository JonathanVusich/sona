package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.ArtistMapper;
import org.sona.model.tables.pojos.Artist;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.excluded;
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
}
