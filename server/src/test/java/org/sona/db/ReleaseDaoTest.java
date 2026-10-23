package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
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
class ReleaseDaoTest {

    @Autowired
    ReleaseDao dao;

    @Autowired
    ReleaseGroupDao releaseGroupDao;

    @Autowired
    ArtistDao artistDao;

    @Autowired
    MediumDao mediumDao;

    @Autowired
    TrackDao trackDao;

    @Autowired
    IngestGroupDao ingestGroupDao;

    @Autowired
    UserDao userDao;

    @Autowired
    TrackToIngestDao trackToIngestDao;

    @Test
    void upsertInsertsNewRelease() {
        final var release = newRelease("A Rush of Blood to the Head", releaseGroupId(), uuidv7());

        assertThat(dao.upsert(release)).isEqualTo(release);
    }

    @Test
    void upsertOverwritesReleaseWithSameMusicBrainzId() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsert(new Release(
                uuidv7(),
                "Old Name",
                null,
                LocalDate.of(2002, 1, 1),
                "GB",
                0,
                releaseGroupId(),
                artistId(),
                musicbrainzId));
        final var newReleaseGroupId = releaseGroupId();

        final var refreshed = dao.upsert(newRelease("New Name", newReleaseGroupId, musicbrainzId));

        assertThat(refreshed)
                .isEqualTo(new Release(
                        stored.releaseId(),
                        "New Name",
                        null,
                        LocalDate.of(2002, 8, 26),
                        "XW",
                        0,
                        newReleaseGroupId,
                        refreshed.artistId(),
                        musicbrainzId));
    }

    @Test
    void releaseCountFollowsTheGroupsReleasesWithATrack() {
        final var musicbrainzId = uuidv7();
        final var first = releaseGroupId();
        final var second = releaseGroupId();
        final var original = dao.upsert(newRelease("A Rush of Blood to the Head", first, musicbrainzId));
        final var withoutTrack = releaseCount(first);
        insertTrack(original);
        insertTrack(original);
        // Not in the library, as it has no track.
        dao.upsert(newRelease("A Rush of Blood to the Head (Japan)", first, uuidv7()));
        // Upserted again, as a retried ingest does.
        dao.upsert(newRelease("A Rush of Blood to the Head", first, musicbrainzId));
        final var withTracks = releaseCount(first);

        // MusicBrainz puts the release in another group now.
        dao.upsert(newRelease("A Rush of Blood to the Head", second, musicbrainzId));

        assertThat(withoutTrack).isZero();
        assertThat(withTracks).isEqualTo(1);
        assertThat(releaseCount(first)).isZero();
        assertThat(releaseCount(second)).isEqualTo(1);
    }

    @Test
    void upsertInsertsEachReleaseWithoutAMusicBrainzId() {
        final var first = newRelease("Bootleg", releaseGroupId(), null);
        final var second = newRelease("Bootleg", releaseGroupId(), null);

        assertThat(dao.upsert(first)).isEqualTo(first);
        assertThat(dao.upsert(second)).isEqualTo(second);
    }

    private int releaseCount(final UUID releaseGroupId) {
        return releaseGroupDao.loadById(releaseGroupId).orElseThrow().releaseCount();
    }

    private UUID releaseGroupId() {
        return releaseGroupDao
                .upsert(new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", null, null, artistId(), 0, uuidv7()))
                .releaseGroupId();
    }

    private UUID artistId() {
        return artistDao
                .upsert(new Artist(uuidv7(), "Coldplay", "GB", null, 0, uuidv7()))
                .artistId();
    }

    private Release newRelease(final String name, final UUID releaseGroupId, final UUID musicbrainzId) {
        return new Release(
                uuidv7(), name, null, LocalDate.of(2002, 8, 26), "XW", 0, releaseGroupId, artistId(), musicbrainzId);
    }

    /**
     * Gives the release a track, so it's in the library.
     */
    private void insertTrack(final Release release) {
        final var medium = mediumDao
                .upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), 1, "CD")))
                .getFirst();
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId());
        ingestGroupDao.insert(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "track.flac", IngestState.COMPLETED);
        trackToIngestDao.insert(ingest);
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
