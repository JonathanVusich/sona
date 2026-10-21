package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ReleaseGroupDaoTest {

    @Autowired
    ReleaseGroupDao dao;

    @Autowired
    ArtistDao artistDao;

    @Test
    void upsertInsertsNewReleaseGroup() {
        final var releaseGroup = new ReleaseGroup(
                uuidv7(),
                "A Rush of Blood to the Head",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2002, 8, 12),
                artistId(),
                0,
                uuidv7());

        assertThat(dao.upsert(releaseGroup)).isEqualTo(releaseGroup);
    }

    @Test
    void upsertOverwritesReleaseGroupWithSameMusicBrainzId() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsert(new ReleaseGroup(
                uuidv7(), "Old Name", ReleaseGroupType.SINGLE, LocalDate.of(2002, 1, 1), artistId(), 0, musicbrainzId));
        final var newArtistId = artistId();

        final var refreshed = dao.upsert(new ReleaseGroup(
                uuidv7(),
                "New Name",
                ReleaseGroupType.ALBUM,
                LocalDate.of(2002, 8, 12),
                newArtistId,
                0,
                musicbrainzId));

        assertThat(refreshed)
                .isEqualTo(new ReleaseGroup(
                        stored.releaseGroupId(),
                        "New Name",
                        ReleaseGroupType.ALBUM,
                        LocalDate.of(2002, 8, 12),
                        newArtistId,
                        0,
                        musicbrainzId));
    }

    private UUID artistId() {
        return artistDao
                .upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()))
                .artistId();
    }
}
