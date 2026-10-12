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
class ReleaseDaoTest {

    @Autowired
    ReleaseDao dao;
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
    void upsertInsertsNewRelease() {
        final var release = newRelease("A Rush of Blood to the Head", releaseGroupId(), uuidv7());

        assertThat(dao.upsert(release)).isEqualTo(release);
    }

    @Test
    void upsertOverwritesReleaseWithSameMusicBrainzId() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsert(new Release(uuidv7(), "Old Name", null, LocalDate.of(2002, 1, 1), "GB",
                releaseGroupId(), null, musicbrainzId));
        final var newReleaseGroupId = releaseGroupId();

        final var refreshed = dao.upsert(newRelease("New Name", newReleaseGroupId, musicbrainzId));

        assertThat(refreshed).isEqualTo(new Release(stored.releaseId(), "New Name", null, LocalDate.of(2002, 8, 26),
                "XW", newReleaseGroupId, null, musicbrainzId));
    }

    @Test
    void loadDetailsReadsTheArtistLabelsByNameAndMediaInOrder() {
        final var artist = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, uuidv7()));
        final var release = dao.upsert(new Release(uuidv7(), "A Rush of Blood to the Head", null,
                LocalDate.of(2002, 8, 26), "GB", releaseGroupId(), artist.artistId(), uuidv7()));
        final var parlophone = storeLabel(release, "Parlophone");
        final var capitol = storeLabel(release, "Capitol");
        final var disc2 = mediumDao.upsert(new Medium(uuidv7(), release.releaseId(), 2, "DVD"));
        final var disc1 = mediumDao.upsert(new Medium(uuidv7(), release.releaseId(), 1, "CD"));

        assertThat(dao.loadDetails(release.releaseId())).contains(
                new ReleaseDetails(release, artist, List.of(capitol, parlophone), List.of(disc1, disc2)));
    }

    @Test
    void loadDetailsByReleaseGroupListsTheEarliestFirst() {
        final var releaseGroupId = releaseGroupId();
        final var undated = dao.upsert(new Release(uuidv7(), "Promo", null, null, null, releaseGroupId, null, uuidv7()));
        final var reissue = dao.upsert(new Release(uuidv7(), "Reissue", null, LocalDate.of(2008, 1, 1), null,
                releaseGroupId, null, uuidv7()));
        final var original = dao.upsert(newRelease("Original", releaseGroupId, uuidv7()));

        assertThat(dao.loadDetailsByReleaseGroup(releaseGroupId))
                .extracting(ReleaseDetails::release)
                .containsExactly(original, reissue, undated);
    }

    private Label storeLabel(final Release release, final String name) {
        final var label = labelDao.upsert(new Label(uuidv7(), name, uuidv7()));
        releaseLabelDao.upsert(new ReleaseLabel(release.releaseId(), label.labelId()));
        return label;
    }

    private UUID releaseGroupId() {
        return releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", null, null, null, uuidv7()))
                .releaseGroupId();
    }

    private static Release newRelease(final String name, final UUID releaseGroupId, final UUID musicbrainzId) {
        return new Release(uuidv7(), name, null, LocalDate.of(2002, 8, 26), "XW", releaseGroupId, null, musicbrainzId);
    }
}
