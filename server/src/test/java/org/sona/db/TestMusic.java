package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;

import java.util.List;
import java.util.UUID;

import static org.sona.utils.DateUtils.parsePartialDate;
import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Stores music in the library as ingestion would, without files or MusicBrainz.
 */
@RequiredArgsConstructor
public final class TestMusic {

    private static final UUID PARLOPHONE_ID = UUID.fromString("df7d1c7f-ef95-425f-8eef-445b3d7bcbd9");

    private final ArtistDao artistDao;
    private final ReleaseGroupDao releaseGroupDao;
    private final ReleaseDao releaseDao;
    private final LabelDao labelDao;
    private final ReleaseLabelDao releaseLabelDao;
    private final MediumDao mediumDao;
    private final TrackDao trackDao;
    private final TrackToIngestDao trackToIngestDao;

    public Artist createArtist(final String name, final String country) {
        return artistDao.upsert(new Artist(uuidv7(), name, country, null, uuidv7()));
    }

    /**
     * Creates an album first released on the date, as precise as MusicBrainz would know it.
     */
    public ReleaseGroup createReleaseGroup(final String name, final Artist artist, final String firstReleaseDate) {
        return releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), name, ReleaseGroupType.ALBUM,
                parsePartialDate(firstReleaseDate), artist.artistId(), uuidv7()));
    }

    /**
     * Creates a British digital release on Parlophone, out on the date, as precise as MusicBrainz would know it.
     */
    public Release createRelease(final ReleaseGroup releaseGroup, final String name, final Artist artist,
                                 final String date) {
        final var release = releaseDao.upsert(new Release(uuidv7(), name, null, parsePartialDate(date), "GB",
                releaseGroup.releaseGroupId(), artist.artistId(), uuidv7()));
        final var label = labelDao.upsertAll(List.of(new Label(uuidv7(), "Parlophone", PARLOPHONE_ID))).getFirst();
        releaseLabelDao.upsertAll(List.of(new ReleaseLabel(release.releaseId(), label.labelId())));
        mediumDao.upsertAll(List.of(new Medium(uuidv7(), release.releaseId(), 1, "Digital Media")));
        return release;
    }

    /**
     * Adds the track after the release's others, on its first disc.
     */
    public Track createTrack(final Release release, final String name, final int duration, final Artist artist) {
        final var ingest = new TrackToIngest(uuidv7(), uuidv7(), name + ".flac", IngestState.COMPLETED);
        trackToIngestDao.storeAll(List.of(ingest));
        final var trackId = uuidv7();
        final var trackNumber = trackDao.loadByReleases(List.of(release.releaseId())).size() + 1;
        final var track = new Track(trackId, name, duration, AudioFormat.FLAC, trackId + ".flac", 1, trackNumber,
                artist.artistId(), release.releaseId(), ingest.trackIngestId());
        trackDao.insert(track);
        return track;
    }
}
