package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

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
    @Autowired
    TrackDao trackDao;
    @Autowired
    TrackToIngestDao trackToIngestDao;

    @Test
    void loadReadsTheArtistLabelsByNameAndMediaInOrder() {
        final var artist = storeArtist("Coldplay");
        final var release = storeRelease(artist);
        final var parlophone = storeLabel(release, "Parlophone");
        final var capitol = storeLabel(release, "Capitol");
        final var disc2 = new Medium(uuidv7(), release.releaseId(), 2, "DVD");
        final var disc1 = new Medium(uuidv7(), release.releaseId(), 1, "CD");
        mediumDao.upsertAll(List.of(disc2, disc1));

        assertThat(dao.load(release.releaseId())).contains(
                new ReleaseDetails(release, artist, List.of(capitol, parlophone), List.of(disc1, disc2), List.of()));
    }

    @Test
    void loadReadsTheTracksInOrderEachWithItsOwnArtist() {
        final var releaseArtist = storeArtist("Various Artists");
        final var coldplay = storeArtist("Coldplay");
        final var keane = storeArtist("Keane");
        final var release = storeRelease(releaseArtist);
        final var secondDisc = storeTrack(release, keane, 2, 1);
        final var second = storeTrack(release, coldplay, 1, 2);
        final var first = storeTrack(release, keane, 1, 1);

        final var details = dao.load(release.releaseId()).orElseThrow();

        assertThat(details.artist()).isEqualTo(releaseArtist);
        assertThat(details.tracks()).containsExactly(new TrackWithArtist(first, keane),
                new TrackWithArtist(second, coldplay), new TrackWithArtist(secondDisc, keane));
    }

    @Test
    void loadCountsTheReleasesTracks() {
        final var artist = storeArtist("Coldplay");
        final var release = storeRelease(artist);
        final var other = storeRelease(artist);
        storeTrack(release, artist, 1, 1);
        storeTrack(release, artist, 1, 2);
        storeTrack(other, artist, 1, 1);

        assertThat(dao.load(release.releaseId()).orElseThrow().release().trackCount()).isEqualTo(2);
    }

    @Test
    void loadFindsNoUnknownRelease() {
        assertThat(dao.load(uuidv7())).isEmpty();
    }

    private Artist storeArtist(final String name) {
        return artistDao.upsert(new Artist(uuidv7(), name, null, null, uuidv7()));
    }

    private Release storeRelease(final Artist artist) {
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head",
                null, null, artist.artistId(), uuidv7()));
        return releaseDao.upsert(new Release(uuidv7(), "A Rush of Blood to the Head", null, LocalDate.of(2002, 8, 26),
                "GB", 0, releaseGroup.releaseGroupId(), artist.artistId(), uuidv7()));
    }

    private Label storeLabel(final Release release, final String name) {
        final var label = labelDao.upsertAll(List.of(new Label(uuidv7(), name, uuidv7()))).getFirst();
        releaseLabelDao.upsertAll(List.of(new ReleaseLabel(release.releaseId(), label.labelId())));
        return label;
    }

    private Track storeTrack(final Release release, final Artist artist, final int discNumber,
                             final int trackNumber) {
        final var ingest = new TrackToIngest(uuidv7(), uuidv7(), "track.flac", IngestState.COMPLETED);
        trackToIngestDao.storeAll(List.of(ingest));
        final var track = new Track(uuidv7(), "Track", 300, AudioFormat.FLAC, "track.flac", discNumber, trackNumber,
                artist.artistId(), release.releaseId(), ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }
}
