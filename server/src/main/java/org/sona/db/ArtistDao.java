package org.sona.db;

import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.RELEASE_GROUP;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.sona.mappers.ArtistMapper;
import org.sona.model.tables.pojos.Artist;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Artists, the artists list and the artist page. Every table the page's query reads is converted to its pojo by its
 * table's mapper as it's read, so the composite mapper builds its result from pojos only.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ArtistDao {

    private final DSLContext dsl;
    private final ArtistMapper artistMapper;

    /**
     * Inserts the artist, or overwrites the stored one with the same MusicBrainz ID with its newer data.
     *
     * @return the stored artist, which keeps its original ID if it already existed
     */
    public Artist upsert(final Artist artist) {
        final var record = dsl.insertInto(ARTIST)
                .set(artistMapper.toRecord(artist))
                .onConflict(ARTIST.MUSICBRAINZ_ARTIST_ID)
                .doUpdate()
                .set(ARTIST.NAME, excluded(ARTIST.NAME))
                .set(ARTIST.COUNTRY, excluded(ARTIST.COUNTRY))
                .returning()
                .fetchSingle();
        return artistMapper.fromRecord(record);
    }

    public Optional<Artist> loadByMusicBrainzId(final UUID musicbrainzArtistId) {
        return dsl.selectFrom(ARTIST)
                .where(ARTIST.MUSICBRAINZ_ARTIST_ID.eq(musicbrainzArtistId))
                .fetchOptional(artistMapper::fromRecord);
    }

    /**
     * Only artists with a release group in the library are listed: one credited only on a track has no page of
     * their own yet.
     *
     * @return the first {@code size} listed artists, by name
     */
    public List<ArtistSummary> loadFirstPage(final int size) {
        return loadPage(noCondition(), size);
    }

    /**
     * Names repeat, so IDs break ties, and the page picks up after the name and ID of the last artist on the page
     * before.
     *
     * @param after the last artist of the page before; an artist who isn't stored has no page after them
     * @return up to {@code size} listed artists, by name, who come after {@code after}
     */
    public List<ArtistSummary> loadNextPage(final UUID after, final int size) {
        return loadPage(afterArtist(after), size);
    }

    /**
     * @return the artist page's data
     */
    public Optional<ArtistDetails> loadDetails(final UUID artistId) {
        return dsl.select(ARTIST.ARTIST_ID, ARTIST.NAME, ARTIST.COUNTRY, releaseGroups())
                .from(ARTIST)
                .where(ARTIST.ARTIST_ID.eq(artistId))
                .fetchOptional(Records.mapping(ArtistDetails::new));
    }

    private List<ArtistSummary> loadPage(final Condition start, final int size) {
        return dsl.select(ARTIST.ARTIST_ID, ARTIST.NAME, ARTIST.RELEASE_GROUP_COUNT)
                .from(ARTIST)
                .where(ARTIST.RELEASE_GROUP_COUNT.gt(0))
                .and(start)
                .orderBy(ARTIST.NAME, ARTIST.ARTIST_ID)
                .limit(size)
                .fetch(Records.mapping(ArtistSummary::new));
    }

    private static Condition afterArtist(final UUID after) {
        final var last = ARTIST.as("last");
        final var lastKey = select(last.NAME, last.ARTIST_ID).from(last).where(last.ARTIST_ID.eq(after));
        return row(ARTIST.NAME, ARTIST.ARTIST_ID).gt(lastKey);
    }

    /**
     * The artist's release groups in the library, those with a release stored, newest first, and those with no known
     * date last.
     */
    private Field<List<ArtistReleaseGroup>> releaseGroups() {
        return multiset(select(
                                RELEASE_GROUP.RELEASE_GROUP_ID,
                                RELEASE_GROUP.NAME,
                                RELEASE_GROUP.TYPE,
                                RELEASE_GROUP.FIRST_RELEASE_DATE)
                        .from(RELEASE_GROUP)
                        .where(RELEASE_GROUP.ARTIST_ID.eq(ARTIST.ARTIST_ID))
                        .and(RELEASE_GROUP.RELEASE_COUNT.gt(0))
                        .orderBy(RELEASE_GROUP.FIRST_RELEASE_DATE.desc().nullsLast(), RELEASE_GROUP.RELEASE_GROUP_ID))
                .convertFrom(groups -> groups.map(Records.mapping(ArtistReleaseGroup::new)));
    }
}
