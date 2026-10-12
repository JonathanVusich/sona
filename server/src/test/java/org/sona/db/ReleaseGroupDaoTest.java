package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ReleaseGroupDaoTest {

    @Autowired
    ReleaseGroupDao dao;

    @Test
    void upsertInsertsNewReleaseGroup() {
        final var releaseGroup = new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", ReleaseGroupType.ALBUM, LocalDate.of(2002, 8, 12), null, uuidv7());

        assertThat(dao.upsert(releaseGroup)).isEqualTo(releaseGroup);
    }

    @Test
    void upsertOverwritesReleaseGroupWithSameMusicBrainzId() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsert(new ReleaseGroup(uuidv7(), "Old Name", ReleaseGroupType.SINGLE, LocalDate.of(2002, 1, 1), null, musicbrainzId));

        final var refreshed = dao.upsert(new ReleaseGroup(uuidv7(), "New Name", ReleaseGroupType.ALBUM, LocalDate.of(2002, 8, 12), null, musicbrainzId));

        assertThat(refreshed).isEqualTo(new ReleaseGroup(stored.releaseGroupId(), "New Name", ReleaseGroupType.ALBUM, LocalDate.of(2002, 8, 12), null, musicbrainzId));
    }
}
