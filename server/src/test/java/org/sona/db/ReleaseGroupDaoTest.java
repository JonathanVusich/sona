package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ReleaseGroupDaoTest {

    @Autowired
    ReleaseGroupDao dao;

    @Test
    void upsertInsertsNewReleaseGroup() {
        final var releaseGroup = new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", uuidv7());

        assertThat(dao.upsert(releaseGroup)).isEqualTo(releaseGroup);
    }

    @Test
    void upsertOverwritesReleaseGroupWithSameMusicBrainzId() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsert(new ReleaseGroup(uuidv7(), "Old Name", musicbrainzId));

        final var refreshed = dao.upsert(new ReleaseGroup(uuidv7(), "New Name", musicbrainzId));

        assertThat(refreshed).isEqualTo(new ReleaseGroup(stored.releaseGroupId(), "New Name", musicbrainzId));
    }
}
