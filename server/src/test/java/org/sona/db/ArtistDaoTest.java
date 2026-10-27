package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ArtistDaoTest {

    @Autowired
    ArtistDao dao;

    @Autowired
    ReleaseGroupDao releaseGroupDao;

    @Autowired
    ReleaseDao releaseDao;

    @Autowired
    TrackDao trackDao;

    @Autowired
    IngestDao ingestDao;

    @Autowired
    UserDao userDao;

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
        insertTrack(insertRelease(upsertReleaseGroup(coldplay, "Parachutes", uuidv7())));
        insertTrack(insertRelease(upsertReleaseGroup(coldplay, "Hopes and Fears", musicbrainzId)));
        // Not in the library until a release of it with a track is stored.
        upsertReleaseGroup(coldplay, "X&Y", uuidv7());
        insertRelease(upsertReleaseGroup(coldplay, "Viva la Vida", uuidv7()));

        // MusicBrainz credits the group to another artist now.
        upsertReleaseGroup(keane, "Hopes and Fears", musicbrainzId);

        assertThat(dao.loadFirstPage(2))
                .extracting(ArtistSummary::name, ArtistSummary::releaseGroupCount)
                .containsExactly(tuple("Coldplay", 1), tuple("Keane", 1));
    }

    @Test
    void loadNextPageFindsNoPageAfterAnUnknownArtist() {
        final var coldplay = dao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()));
        insertTrack(insertRelease(upsertReleaseGroup(coldplay, "Parachutes", uuidv7())));

        assertThat(dao.loadNextPage(uuidv7(), 10)).isEmpty();
    }

    private ReleaseGroup upsertReleaseGroup(final Artist artist, final String name, final UUID musicbrainzId) {
        return releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                name,
                ReleaseGroupType.ALBUM,
                LocalDate.of(2000, 7, 10),
                artist.artistId(),
                0,
                musicbrainzId));
    }

    private Release insertRelease(final ReleaseGroup group) {
        return releaseDao.upsert(new Release(
                uuidv7(),
                group.name(),
                null,
                group.firstReleaseDate(),
                "GB",
                0,
                group.releaseGroupId(),
                group.artistId(),
                uuidv7()));
    }

    /**
     * Gives the release a track, so it's in the library.
     */
    private void insertTrack(final Release release) {
        final var medium = releaseDao
                .upsertMedia(List.of(new Medium(uuidv7(), release.releaseId(), 1, "CD")))
                .getFirst();
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId());
        ingestDao.insertGroup(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "track.flac", IngestState.COMPLETED);
        ingestDao.insertTrackIngest(ingest);
        trackDao.insert(new Track(
                uuidv7(),
                release.name(),
                300,
                AudioFormat.FLAC,
                "track.flac",
                medium.mediumId(),
                1,
                release.artistId(),
                ingest.trackIngestId()));
    }
}
