package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ReleaseDetailsDaoTest {

    @Autowired
    ReleaseDetailsDao dao;
    @Autowired
    ReleaseDao releaseDao;
    @Autowired
    ReleaseGroupDao releaseGroupDao;
    @Autowired
    ArtistDao artistDao;
    @Autowired
    LabelDao labelDao;
    @Autowired
    ReleaseLabelDao releaseLabelDao;
    @Autowired
    MediumDao mediumDao;

    @Test
    void loadReadsTheArtistLabelsByNameAndMediaInOrder() {
        final var artist = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, uuidv7()));
        final var release = releaseDao.upsert(new Release(uuidv7(), "A Rush of Blood to the Head", null,
                LocalDate.of(2002, 8, 26), "GB", 0, releaseGroupId(), artist.artistId(), uuidv7()));
        final var parlophone = storeLabel(release, "Parlophone");
        final var capitol = storeLabel(release, "Capitol");
        final var disc2 = new Medium(uuidv7(), release.releaseId(), 2, "DVD");
        final var disc1 = new Medium(uuidv7(), release.releaseId(), 1, "CD");
        mediumDao.upsertAll(List.of(disc2, disc1));

        assertThat(dao.load(release.releaseId())).contains(
                new ReleaseDetails(release, artist, List.of(capitol, parlophone), List.of(disc1, disc2)));
    }

    @Test
    void loadByReleaseGroupListsTheEarliestFirst() {
        final var releaseGroupId = releaseGroupId();
        final var undated = storeRelease("Promo", null, releaseGroupId);
        final var reissue = storeRelease("Reissue", LocalDate.of(2008, 1, 1), releaseGroupId);
        final var original = storeRelease("Original", LocalDate.of(2002, 8, 26), releaseGroupId);

        assertThat(dao.loadByReleaseGroup(releaseGroupId))
                .extracting(ReleaseDetails::release)
                .containsExactly(original, reissue, undated);
    }

    private Release storeRelease(final String name, final LocalDate date, final UUID releaseGroupId) {
        return releaseDao.upsert(new Release(uuidv7(), name, null, date, null, 0, releaseGroupId, artistId(),
                uuidv7()));
    }

    private Label storeLabel(final Release release, final String name) {
        final var label = labelDao.upsertAll(List.of(new Label(uuidv7(), name, uuidv7()))).getFirst();
        releaseLabelDao.upsertAll(List.of(new ReleaseLabel(release.releaseId(), label.labelId())));
        return label;
    }

    private UUID releaseGroupId() {
        return releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", null, null, artistId(),
                uuidv7())).releaseGroupId();
    }

    private UUID artistId() {
        return artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, uuidv7())).artistId();
    }
}
