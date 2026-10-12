package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class MediumDaoTest {

    @Autowired
    MediumDao dao;
    @Autowired
    ReleaseDao releaseDao;
    @Autowired
    ReleaseGroupDao releaseGroupDao;
    @Autowired
    ArtistDao artistDao;

    @Test
    void upsertAllInsertsNewMedia() {
        final var releaseId = releaseId();
        final var media = List.of(new Medium(uuidv7(), releaseId, 1, "CD"), new Medium(uuidv7(), releaseId, 2, "DVD"));

        assertThat(dao.upsertAll(media)).containsExactlyInAnyOrderElementsOf(media);
    }

    @Test
    void upsertAllOverwritesMediaAtTheSamePositionAndInsertsTheRest() {
        final var releaseId = releaseId();
        final var stored = dao.upsertAll(List.of(new Medium(uuidv7(), releaseId, 1, null))).getFirst();
        final var dvd = new Medium(uuidv7(), releaseId, 2, "DVD");

        final var refreshed = dao.upsertAll(List.of(new Medium(uuidv7(), releaseId, 1, "CD"), dvd));

        assertThat(refreshed).containsExactlyInAnyOrder(new Medium(stored.mediumId(), releaseId, 1, "CD"), dvd);
    }

    private UUID releaseId() {
        final var artist = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, uuidv7()));
        final var releaseGroup = releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", null, null, artist.artistId(), uuidv7()));
        return releaseDao.upsert(new Release(uuidv7(), "A Rush of Blood to the Head", null, null, null,
                releaseGroup.releaseGroupId(), artist.artistId(), uuidv7())).releaseId();
    }
}
