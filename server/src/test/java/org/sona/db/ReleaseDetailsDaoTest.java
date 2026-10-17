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
        final var disc2 = storeMedium(release, 2);
        final var disc1 = storeMedium(release, 1);

        assertThat(dao.loadById(release.releaseId())).contains(new ReleaseDetails(release,
                withOneReleaseGroup(artist), List.of(capitol, parlophone),
                List.of(new MediumWithTracks(disc1, List.of()), new MediumWithTracks(disc2, List.of()))));
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
        assertThat(details.media()).containsExactly(
                new MediumWithTracks(disc1, List.of(new TrackWithArtist(first, keane),
                        new TrackWithArtist(second, coldplay))),
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

        assertThat(dao.loadById(release.releaseId()).orElseThrow().release().trackCount()).isEqualTo(2);
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
        return new Artist(artist.artistId(), artist.name(), artist.country(), artist.thumbnailPath(), 1,
                artist.musicbrainzArtistId());
    }

    private Release storeRelease(final Artist artist) {
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head",
                null, null, artist.artistId(), 0, uuidv7()));
        return releaseDao.upsert(new Release(uuidv7(), "A Rush of Blood to the Head", null, LocalDate.of(2002, 8, 26),
                "GB", 0, releaseGroup.releaseGroupId(), artist.artistId(), uuidv7()));
    }

    private Label storeLabel(final Release release, final String name) {
        final var label = labelDao.upsertAll(List.of(new Label(uuidv7(), name, uuidv7()))).getFirst();
        releaseLabelDao.upsertAll(List.of(new ReleaseLabel(release.releaseId(), label.labelId())));
        return label;
    }

    private Medium storeMedium(final Release release, final int position) {
        return mediumDao.upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), position, "CD"))).getFirst();
    }

    private Track storeTrack(final Medium medium, final Artist artist, final int position) {
        final var ingest = new TrackToIngest(uuidv7(), uuidv7(), "track.flac", IngestState.COMPLETED);
        trackToIngestDao.insert(ingest);
        final var track = new Track(uuidv7(), "Track", 300, AudioFormat.FLAC, "track.flac", medium.mediumId(),
                position, artist.artistId(), ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }
}
