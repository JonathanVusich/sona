package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ReleaseGroupWithArtistDaoTest {

    @Autowired
    ReleaseGroupWithArtistDao dao;
    @Autowired
    ReleaseGroupDao releaseGroupDao;
    @Autowired
    ArtistDao artistDao;

    @Test
    void loadReadsTheReleaseGroupsArtist() {
        final var artist = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, uuidv7()));
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "Parachutes", ReleaseGroupType.ALBUM,
                LocalDate.of(2000, 7, 10), artist.artistId(), uuidv7()));

        assertThat(dao.load(releaseGroup.releaseGroupId()))
                .contains(new ReleaseGroupWithArtist(releaseGroup, artist));
    }
}
