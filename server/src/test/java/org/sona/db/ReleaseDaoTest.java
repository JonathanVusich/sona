package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ReleaseDaoTest {

    @Autowired
    ReleaseDao dao;
    @Autowired
    ReleaseGroupDao releaseGroupDao;

    @Test
    void upsertInsertsNewRelease() {
        final var release = release("A Rush of Blood to the Head", releaseGroupId(), uuidv7());

        assertThat(dao.upsert(release)).isEqualTo(release);
    }

    @Test
    void upsertOverwritesReleaseWithSameMusicBrainzId() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsert(release("Old Name", releaseGroupId(), musicbrainzId));
        final var newReleaseGroupId = releaseGroupId();

        final var refreshed = dao.upsert(release("New Name", newReleaseGroupId, musicbrainzId));

        assertThat(refreshed).isEqualTo(
                new Release(stored.releaseId(), "New Name", null, newReleaseGroupId, null, musicbrainzId));
    }

    private UUID releaseGroupId() {
        return releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", uuidv7()))
                .releaseGroupId();
    }

    private static Release release(final String name, final UUID releaseGroupId, final UUID musicbrainzId) {
        return new Release(uuidv7(), name, null, releaseGroupId, null, musicbrainzId);
    }
}
