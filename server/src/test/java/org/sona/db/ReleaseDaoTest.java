package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ReleaseDaoTest {

    @Autowired
    ReleaseDao dao;
    @Autowired
    ReleaseGroupDao releaseGroupDao;
    @Autowired
    ArtistDao artistDao;

    @Test
    void upsertInsertsNewRelease() {
        final var release = newRelease("A Rush of Blood to the Head", releaseGroupId(), uuidv7());

        assertThat(dao.upsert(release)).isEqualTo(release);
    }

    @Test
    void upsertOverwritesReleaseWithSameMusicBrainzId() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsert(new Release(uuidv7(), "Old Name", null, LocalDate.of(2002, 1, 1), "GB", 0,
                releaseGroupId(), artistId(), musicbrainzId));
        final var newReleaseGroupId = releaseGroupId();

        final var refreshed = dao.upsert(newRelease("New Name", newReleaseGroupId, musicbrainzId));

        assertThat(refreshed).isEqualTo(new Release(stored.releaseId(), "New Name", null, LocalDate.of(2002, 8, 26),
                "XW", 0, newReleaseGroupId, refreshed.artistId(), musicbrainzId));
    }

    @Test
    void upsertInsertsEachReleaseWithoutAMusicBrainzId() {
        final var first = newRelease("Bootleg", releaseGroupId(), null);
        final var second = newRelease("Bootleg", releaseGroupId(), null);

        assertThat(dao.upsert(first)).isEqualTo(first);
        assertThat(dao.upsert(second)).isEqualTo(second);
    }

    private UUID releaseGroupId() {
        return releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", null, null, artistId(),
                uuidv7())).releaseGroupId();
    }

    private UUID artistId() {
        return artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, uuidv7())).artistId();
    }

    private Release newRelease(final String name, final UUID releaseGroupId, final UUID musicbrainzId) {
        return new Release(uuidv7(), name, null, LocalDate.of(2002, 8, 26), "XW", 0, releaseGroupId, artistId(),
                musicbrainzId);
    }
}
