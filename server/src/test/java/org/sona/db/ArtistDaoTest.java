package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class ArtistDaoTest {

    @Autowired
    ArtistDao dao;
    @Autowired
    ReleaseGroupDao releaseGroupDao;
    @Autowired
    ReleaseDao releaseDao;

    @Test
    void upsertInsertsNewArtist() {
        final var artist = new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7());

        assertThat(dao.upsert(artist)).isEqualTo(artist);
    }

    @Test
    void upsertOverwritesArtistWithSameMusicBrainzId() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsert(new Artist(uuidv7(), "Old Name", "GB", null, 0, musicbrainzId));

        final var refreshed = dao.upsert(new Artist(uuidv7(), "New Name", "US", null, 0, musicbrainzId));

        assertThat(refreshed).isEqualTo(new Artist(stored.artistId(), "New Name", "US", null, 0, musicbrainzId));
    }

    @Test
    void releaseGroupCountFollowsTheArtistsReleaseGroupsWithARelease() {
        final var coldplay = dao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        final var keane = dao.upsert(new Artist(uuidv7(), "Keane", "GB", null, 0, uuidv7()));
        final var musicbrainzId = uuidv7();
        insertRelease(upsertReleaseGroup(coldplay, "Parachutes", uuidv7()));
        insertRelease(upsertReleaseGroup(coldplay, "Hopes and Fears", musicbrainzId));
        // Not in the library until a release of it is stored.
        upsertReleaseGroup(coldplay, "X&Y", uuidv7());

        // MusicBrainz credits the group to another artist now.
        upsertReleaseGroup(keane, "Hopes and Fears", musicbrainzId);

        assertThat(dao.loadFirstPage(2)).extracting(Artist::name, Artist::releaseGroupCount)
                .containsExactly(tuple("Coldplay", 1), tuple("Keane", 1));
    }

    @Test
    void loadNextPageFindsNoPageAfterAnUnknownArtist() {
        final var coldplay = dao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        insertRelease(upsertReleaseGroup(coldplay, "Parachutes", uuidv7()));

        assertThat(dao.loadNextPage(uuidv7(), 10)).isEmpty();
    }

    private ReleaseGroup upsertReleaseGroup(final Artist artist, final String name, final UUID musicbrainzId) {
        return releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), name, ReleaseGroupType.ALBUM,
                LocalDate.of(2000, 7, 10), artist.artistId(), 0, musicbrainzId));
    }

    private void insertRelease(final ReleaseGroup group) {
        releaseDao.upsert(new Release(uuidv7(), group.name(), null, group.firstReleaseDate(), "GB", 0,
                group.releaseGroupId(), group.artistId(), uuidv7()));
    }
}
