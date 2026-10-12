package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.tables.pojos.Artist;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ArtistDaoTest {

    @Autowired
    ArtistDao dao;

    @Test
    void upsertInsertsNewArtist() {
        final var artist = new Artist(uuidv7(), "Coldplay", "GB", null, uuidv7());

        assertThat(dao.upsert(artist)).isEqualTo(artist);
    }

    @Test
    void upsertOverwritesArtistWithSameMusicBrainzId() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsert(new Artist(uuidv7(), "Old Name", "GB", null, musicbrainzId));

        final var refreshed = dao.upsert(new Artist(uuidv7(), "New Name", "US", null, musicbrainzId));

        assertThat(refreshed).isEqualTo(new Artist(stored.artistId(), "New Name", "US", null, musicbrainzId));
    }
}
