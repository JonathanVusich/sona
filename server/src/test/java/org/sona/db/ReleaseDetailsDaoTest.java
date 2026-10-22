package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Autowired
    TrackDao trackDao;

    @Autowired
    IngestGroupDao ingestGroupDao;

    @Autowired
    TrackToIngestDao trackToIngestDao;

    @Test
    void loadReadsTheArtistLabelsByNameAndMediaInOrder() {
        final var artist = storeArtist("Coldplay");
        final var keane = storeArtist("Keane");
        final var release = storeRelease(artist);
        final var parlophone = storeLabel(release, "Parlophone");
        final var capitol = storeLabel(release, "Capitol");
        final var disc2 = storeMedium(release, 2);
        final var disc1 = storeMedium(release, 1);
        final var track = storeTrack(disc1, keane, 1);

        assertThat(dao.loadById(release.releaseId()))
                .contains(new ReleaseDetails(
                        withOneTrack(release),
                        withOneReleaseGroup(artist),
                        List.of(capitol, parlophone),
                        List.of(
                                new MediumWithTracks(disc1, List.of(new TrackWithArtist(track, keane))),
                                new MediumWithTracks(disc2, List.of()))));
    }

    @Test
    void loadReadsEachMediumsTracksInOrderEachWithItsOwnArtist() {
        final var releaseArtist = storeArtist("Various Artists");
        final var coldplay = storeArtist("Coldplay");
        final var keane = storeArtist("Keane");
        final var release = storeRelease(releaseArtist);
        final var disc1 = storeMedium(release, 1);
        final var disc2 = storeMedium(release, 2);
        final var bonus = storeTrack(disc2, keane, 1);
        final var second = storeTrack(disc1, coldplay, 2);
        final var first = storeTrack(disc1, keane, 1);

        final var details = dao.loadById(release.releaseId()).orElseThrow();

        assertThat(details.artist()).isEqualTo(withOneReleaseGroup(releaseArtist));
        assertThat(details.media())
                .containsExactly(
                        new MediumWithTracks(
                                disc1,
                                List.of(new TrackWithArtist(first, keane), new TrackWithArtist(second, coldplay))),
                        new MediumWithTracks(disc2, List.of(new TrackWithArtist(bonus, keane))));
    }

    @Test
    void loadCountsTheTracksOnAllOfTheReleasesMedia() {
        final var artist = storeArtist("Coldplay");
        final var release = storeRelease(artist);
        final var disc1 = storeMedium(release, 1);
        final var disc2 = storeMedium(release, 2);
        storeTrack(disc1, artist, 1);
        storeTrack(disc2, artist, 1);
        storeTrack(storeMedium(storeRelease(artist), 1), artist, 1);

        assertThat(dao.loadById(release.releaseId()).orElseThrow().release().trackCount())
                .isEqualTo(2);
    }

    @Test
    void loadLeavesOutTracksWhoseFileIsntInTheLibrary() {
        final var artist = storeArtist("Coldplay");
        final var keane = storeArtist("Keane");
        final var release = storeRelease(artist);
        final var disc = storeMedium(release, 1);
        final var imported = storeTrack(disc, keane, 1);
        storeTrackInState(disc, keane, 2, IngestState.MOVE_FAILED);
        storeTrackInState(disc, keane, 3, IngestState.PENDING);

        final var details = dao.loadById(release.releaseId()).orElseThrow();

        assertThat(details.media())
                .containsExactly(new MediumWithTracks(disc, List.of(new TrackWithArtist(imported, keane))));
        assertThat(details.release().trackCount()).isEqualTo(1);
    }

    @Test
    void trackCountFollowsTheTracksIngest() {
        final var artist = storeArtist("Coldplay");
        final var release = storeRelease(artist);
        final var track = storeTrackInState(storeMedium(release, 1), artist, 1, IngestState.PENDING);

        trackToIngestDao.updateState(track.trackIngestId(), IngestState.MOVE_FAILED);
        final var failed = trackCount(release);
        trackToIngestDao.updateState(track.trackIngestId(), IngestState.COMPLETED);
        final var completed = trackCount(release);

        assertThat(failed).isZero();
        assertThat(completed).isEqualTo(1);
    }

    @Test
    void loadFindsNoReleaseWithoutATrackInTheLibrary() {
        final var artist = storeArtist("Coldplay");
        final var release = storeRelease(artist);
        storeTrackInState(storeMedium(release, 1), artist, 1, IngestState.MOVE_FAILED);

        assertThat(dao.loadById(release.releaseId())).isEmpty();
    }

    @Test
    void loadFindsNoUnknownRelease() {
        assertThat(dao.loadById(uuidv7())).isEmpty();
    }

    private Artist storeArtist(final String name) {
        return artistDao.upsert(new Artist(uuidv7(), name, null, null, 0, uuidv7()));
    }

    /**
     * @return the artist as stored once {@link #storeRelease} has stored a release group of theirs
     */
    private static Artist withOneReleaseGroup(final Artist artist) {
        return new Artist(
                artist.artistId(),
                artist.name(),
                artist.country(),
                artist.thumbnailPath(),
                1,
                artist.musicbrainzArtistId());
    }

    /**
     * @return the release as stored once one track of it has been
     */
    private static Release withOneTrack(final Release release) {
        return new Release(
                release.releaseId(),
                release.name(),
                release.thumbnailPath(),
                release.date(),
                release.country(),
                1,
                release.releaseGroupId(),
                release.artistId(),
                release.musicbrainzReleaseId());
    }

    private int trackCount(final Release release) {
        return releaseDao
                .loadByMusicBrainzId(release.musicbrainzReleaseId())
                .orElseThrow()
                .trackCount();
    }

    private Release storeRelease(final Artist artist) {
        final var releaseGroup = releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", null, null, artist.artistId(), 0, uuidv7()));
        return releaseDao.upsert(new Release(
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
                labelDao.upsertAll(List.of(new Label(uuidv7(), name, uuidv7()))).getFirst();
        releaseLabelDao.upsertAll(List.of(new ReleaseLabel(release.releaseId(), label.labelId())));
        return label;
    }

    private Medium storeMedium(final Release release, final int position) {
        return mediumDao
                .upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), position, "CD")))
                .getFirst();
    }

    private Track storeTrack(final Medium medium, final Artist artist, final int position) {
        return storeTrackInState(medium, artist, position, IngestState.COMPLETED);
    }

    private Track storeTrackInState(
            final Medium medium, final Artist artist, final int position, final IngestState state) {
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads");
        ingestGroupDao.upsert(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "track.flac", state);
        trackToIngestDao.insert(ingest);
        final var track = new Track(
                uuidv7(),
                "Track",
                300,
                AudioFormat.FLAC,
                "track.flac",
                medium.mediumId(),
                position,
                artist.artistId(),
                ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }
}
