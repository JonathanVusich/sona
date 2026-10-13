package org.sona.db;

import lombok.RequiredArgsConstructor;
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

import java.time.LocalDate;
import java.util.List;

import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Stores music in the library as ingestion would, for tests of what the API reads back.
 */
@RequiredArgsConstructor
public final class TestLibrary {

    private final ArtistDao artistDao;
    private final ReleaseGroupDao releaseGroupDao;
    private final ReleaseDao releaseDao;
    private final LabelDao labelDao;
    private final ReleaseLabelDao releaseLabelDao;
    private final MediumDao mediumDao;
    private final TrackDao trackDao;
    private final TrackToIngestDao trackToIngestDao;

    public Artist storeArtist(final String name) {
        return artistDao.upsert(new Artist(uuidv7(), name, "GB", null, uuidv7()));
    }

    public Release storeRelease(final Artist artist, final String name) {
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), name, null, null,
                artist.artistId(), uuidv7()));
        return releaseDao.upsert(new Release(uuidv7(), name, null, LocalDate.of(2002, 8, 26), "GB", 0,
                releaseGroup.releaseGroupId(), artist.artistId(), uuidv7()));
    }

    public Label storeLabel(final Release release, final String name) {
        final var label = labelDao.upsertAll(List.of(new Label(uuidv7(), name, uuidv7()))).getFirst();
        releaseLabelDao.upsertAll(List.of(new ReleaseLabel(release.releaseId(), label.labelId())));
        return label;
    }

    public Medium storeMedium(final Release release, final int position) {
        return mediumDao.upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), position, "CD"))).getFirst();
    }

    public Track storeTrack(final Medium medium, final Artist artist, final int position, final String name) {
        final var ingest = new TrackToIngest(uuidv7(), uuidv7(), "track.flac", IngestState.COMPLETED);
        trackToIngestDao.insert(ingest);
        final var track = new Track(uuidv7(), name, 300, AudioFormat.FLAC, "track.flac", medium.mediumId(),
                position, artist.artistId(), ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }
}
