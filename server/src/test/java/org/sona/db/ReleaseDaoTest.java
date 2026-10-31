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
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.ReleaseLabel;
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
    TrackDao trackDao;

    @Autowired
    IngestDao ingestDao;

    @Autowired
    UserDao userDao;

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

    @Test
    void loadDetailsReadsTheArtistLabelsByNameAndMediaInOrder() {
        final var artist = storeArtist("Coldplay");
        final var keane = storeArtist("Keane");
        final var release = storeRelease(artist);
        final var parlophone = storeLabel(release, "Parlophone");
        final var capitol = storeLabel(release, "Capitol");
        final var disc2 = storeMedium(release, 2);
        final var disc1 = storeMedium(release, 1);
        final var track = storeTrack(disc1, keane, 1);

        assertThat(dao.loadDetails(release.releaseId()))
                .contains(new ReleaseDetails(
                        release.releaseId(),
                        release.name(),
                        release.date(),
                        release.country(),
                        1,
                        release.releaseGroupId(),
                        artist.artistId(),
                        artist.name(),
                        List.of(
                                new LabelSummary(capitol.labelId(), capitol.name()),
                                new LabelSummary(parlophone.labelId(), parlophone.name())),
                        List.of(mediumWithTracks(disc1, trackWithArtist(track, keane)), mediumWithTracks(disc2))));
    }

    @Test
    void loadDetailsReadsEachMediumsTracksInOrderEachWithItsOwnArtist() {
        final var releaseArtist = storeArtist("Various Artists");
        final var coldplay = storeArtist("Coldplay");
        final var keane = storeArtist("Keane");
        final var release = storeRelease(releaseArtist);
        final var disc1 = storeMedium(release, 1);
        final var disc2 = storeMedium(release, 2);
        final var bonus = storeTrack(disc2, keane, 1);
        final var second = storeTrack(disc1, coldplay, 2);
        final var first = storeTrack(disc1, keane, 1);

        final var details = dao.loadDetails(release.releaseId()).orElseThrow();

        assertThat(details.artistId()).isEqualTo(releaseArtist.artistId());
        assertThat(details.artistName()).isEqualTo(releaseArtist.name());
        assertThat(details.media())
                .containsExactly(
                        mediumWithTracks(disc1, trackWithArtist(first, keane), trackWithArtist(second, coldplay)),
                        mediumWithTracks(disc2, trackWithArtist(bonus, keane)));
    }

    @Test
    void loadDetailsCountsTheTracksOnAllOfTheReleasesMedia() {
        final var artist = storeArtist("Coldplay");
        final var release = storeRelease(artist);
        final var disc1 = storeMedium(release, 1);
        final var disc2 = storeMedium(release, 2);
        storeTrack(disc1, artist, 1);
        storeTrack(disc2, artist, 1);
        storeTrack(storeMedium(storeRelease(artist), 1), artist, 1);

        assertThat(dao.loadDetails(release.releaseId()).orElseThrow().trackCount())
                .isEqualTo(2);
    }

    @Test
    void loadDetailsLeavesOutTracksWhoseFileIsntInTheLibrary() {
        final var artist = storeArtist("Coldplay");
        final var keane = storeArtist("Keane");
        final var release = storeRelease(artist);
        final var disc = storeMedium(release, 1);
        final var imported = storeTrack(disc, keane, 1);
        storeTrackInState(disc, keane.artistId(), 2, IngestState.MOVE_FAILED);
        storeTrackInState(disc, keane.artistId(), 3, IngestState.PENDING);

        final var details = dao.loadDetails(release.releaseId()).orElseThrow();

        assertThat(details.media()).containsExactly(mediumWithTracks(disc, trackWithArtist(imported, keane)));
        assertThat(details.trackCount()).isEqualTo(1);
    }

    @Test
    void trackCountFollowsTheTracksIngest() {
        final var artist = storeArtist("Coldplay");
        final var release = storeRelease(artist);
        final var track = storeTrackInState(storeMedium(release, 1), artist.artistId(), 1, IngestState.PENDING);

        ingestDao.updateState(track.trackIngestId(), IngestState.MOVE_FAILED);
        final var failed = trackCount(release);
        ingestDao.updateState(track.trackIngestId(), IngestState.COMPLETED);
        final var completed = trackCount(release);

        assertThat(failed).isZero();
        assertThat(completed).isEqualTo(1);
    }

    @Test
    void loadDetailsFindsNoReleaseWithoutATrackInTheLibrary() {
        final var artist = storeArtist("Coldplay");
        final var release = storeRelease(artist);
        storeTrackInState(storeMedium(release, 1), artist.artistId(), 1, IngestState.MOVE_FAILED);

        assertThat(dao.loadDetails(release.releaseId())).isEmpty();
    }

    @Test
    void loadDetailsFindsNoUnknownRelease() {
        assertThat(dao.loadDetails(uuidv7())).isEmpty();
    }

    @Test
    void upsertLabelsInsertsNewLabels() {
        final var labels =
                List.of(new Label(uuidv7(), "Parlophone", uuidv7()), new Label(uuidv7(), "Capitol", uuidv7()));

        assertThat(dao.upsertLabels(labels)).containsExactlyInAnyOrderElementsOf(labels);
    }

    @Test
    void upsertLabelsOverwritesLabelsWithSameMusicBrainzIdAndInsertsTheRest() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsertLabels(List.of(new Label(uuidv7(), "Old Name", musicbrainzId)))
                .getFirst();
        final var capitol = new Label(uuidv7(), "Capitol", uuidv7());

        final var refreshed = dao.upsertLabels(List.of(new Label(uuidv7(), "New Name", musicbrainzId), capitol));

        assertThat(refreshed)
                .containsExactlyInAnyOrder(new Label(stored.labelId(), "New Name", musicbrainzId), capitol);
    }

    @Test
    void upsertLabelsOfNoLabelsStoresNothing() {
        assertThat(dao.upsertLabels(List.of())).isEmpty();
    }

    @Test
    void upsertMediaInsertsNewMedia() {
        final var releaseId = storeRelease(storeArtist("Coldplay")).releaseId();
        final var media = List.of(new Medium(uuidv7(), releaseId, 1, "CD"), new Medium(uuidv7(), releaseId, 2, "DVD"));

        assertThat(dao.upsertMedia(media)).containsExactlyInAnyOrderElementsOf(media);
    }

    @Test
    void upsertMediaOverwritesMediaAtTheSamePositionAndInsertsTheRest() {
        final var releaseId = storeRelease(storeArtist("Coldplay")).releaseId();
        final var stored = dao.upsertMedia(List.of(new Medium(uuidv7(), releaseId, 1, null)))
                .getFirst();
        final var dvd = new Medium(uuidv7(), releaseId, 2, "DVD");

        final var refreshed = dao.upsertMedia(List.of(new Medium(uuidv7(), releaseId, 1, "CD"), dvd));

        assertThat(refreshed).containsExactlyInAnyOrder(new Medium(stored.mediumId(), releaseId, 1, "CD"), dvd);
    }

    @Test
    void loadMediumByPositionFindsTheReleasesMediumAtThatPosition() {
        final var artist = storeArtist("Coldplay");
        final var releaseId = storeRelease(artist).releaseId();
        final var cd = new Medium(uuidv7(), releaseId, 1, "CD");
        final var dvd = new Medium(uuidv7(), releaseId, 2, "DVD");
        dao.upsertMedia(
                List.of(cd, dvd, new Medium(uuidv7(), storeRelease(artist).releaseId(), 2, "Vinyl")));

        assertThat(dao.loadMediumByPosition(releaseId, 2)).contains(dvd);
        assertThat(dao.loadMediumByPosition(releaseId, 3)).isEmpty();
    }

    private int releaseCount(final UUID releaseGroupId) {
        return releaseGroupDao.loadById(releaseGroupId).orElseThrow().releaseCount();
    }

    private int trackCount(final Release release) {
        return dao.loadByMusicBrainzId(release.musicbrainzReleaseId())
                .orElseThrow()
                .trackCount();
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

    private Artist storeArtist(final String name) {
        return artistDao.upsert(new Artist(uuidv7(), name, null, null, 0, uuidv7()));
    }

    private static MediumWithTracks mediumWithTracks(final Medium medium, final TrackWithArtist... tracks) {
        return new MediumWithTracks(medium.position(), medium.format(), List.of(tracks));
    }

    private static TrackWithArtist trackWithArtist(final Track track, final Artist artist) {
        return new TrackWithArtist(
                track.trackId(), track.name(), track.duration(), track.position(), artist.artistId(), artist.name());
    }

    private Release storeRelease(final Artist artist) {
        final var releaseGroup = releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", null, null, artist.artistId(), 0, uuidv7()));
        return dao.upsert(new Release(
                uuidv7(),
                "A Rush of Blood to the Head",
                null,
                LocalDate.of(2002, 8, 26),
                "GB",
                0,
                releaseGroup.releaseGroupId(),
                artist.artistId(),
                uuidv7()));
    }

    private Label storeLabel(final Release release, final String name) {
        final var label =
                dao.upsertLabels(List.of(new Label(uuidv7(), name, uuidv7()))).getFirst();
        dao.upsertReleaseLabels(List.of(new ReleaseLabel(release.releaseId(), label.labelId())));
        return label;
    }

    private Medium storeMedium(final Release release, final int position) {
        return dao.upsertMedia(List.of(new Medium(uuidv7(), release.releaseId(), position, "CD")))
                .getFirst();
    }

    /**
     * Gives the release a track, so it's in the library.
     */
    private void insertTrack(final Release release) {
        storeTrackInState(storeMedium(release, 1), release.artistId(), 1, IngestState.COMPLETED);
    }

    private Track storeTrack(final Medium medium, final Artist artist, final int position) {
        return storeTrackInState(medium, artist.artistId(), position, IngestState.COMPLETED);
    }

    private Track storeTrackInState(
            final Medium medium, final UUID artistId, final int position, final IngestState state) {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId(), 1);
        ingestDao.insertGroup(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "track.flac", state);
        ingestDao.insertTrackIngest(ingest);
        final var track = new Track(
                uuidv7(),
                "Track",
                300,
                AudioFormat.FLAC,
                "track.flac",
                medium.mediumId(),
                position,
                artistId,
                ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }
}
