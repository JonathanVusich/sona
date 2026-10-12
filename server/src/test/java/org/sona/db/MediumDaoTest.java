package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Test
    void upsertInsertsNewMedium() {
        final var medium = new Medium(uuidv7(), releaseId(), 1, "CD");

        assertThat(dao.upsert(medium)).isEqualTo(medium);
    }

    @Test
    void upsertOverwritesMediumAtTheSamePosition() {
        final var releaseId = releaseId();
        final var stored = dao.upsert(new Medium(uuidv7(), releaseId, 1, null));

        final var refreshed = dao.upsert(new Medium(uuidv7(), releaseId, 1, "Digital Media"));

        assertThat(refreshed).isEqualTo(new Medium(stored.mediumId(), releaseId, 1, "Digital Media"));
    }

    private UUID releaseId() {
        final var releaseGroup = releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", null, null, null, uuidv7()));
        return releaseDao.upsert(new Release(uuidv7(), "A Rush of Blood to the Head", null, null, null,
                releaseGroup.releaseGroupId(), null, uuidv7())).releaseId();
    }
}
