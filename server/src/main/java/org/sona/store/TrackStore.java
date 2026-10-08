package org.sona.store;

import lombok.RequiredArgsConstructor;
import org.sona.db.ArtistDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.format.Format;
import org.sona.library.Library;
import org.sona.metadata.resolver.ResolvedArtist;
import org.sona.metadata.resolver.ResolvedTrack;
import org.sona.model.enums.AudioFormat;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Stores imported tracks with their artists, release groups and releases.
 */
@Service
@RequiredArgsConstructor
public class TrackStore {

    private final Library library;
    private final ArtistDao artistDao;
    private final ReleaseGroupDao releaseGroupDao;
    private final ReleaseDao releaseDao;
    private final TrackDao trackDao;

    /**
     * Stores the track together with the artist, release group and release rows it references.
     *
     * @return the stored track
     */
    @Transactional
    public Track storeTrack(final TrackToIngest trackToIngest,
                            final ResolvedTrack resolved,
                            final Duration duration,
                            final Format format) {
        // Tracks from the same MusicBrainz release share its rows, and so its library folder.
        final var artist = upsertArtist(resolved.artist());
        final var releaseArtist = upsertArtist(resolved.releaseArtist());
        final var releaseGroup = releaseGroupDao.upsert(
                new ReleaseGroup(uuidv7(), resolved.releaseGroupTitle(), resolved.releaseGroupId()));
        final var release = releaseDao.upsert(new Release(uuidv7(), resolved.releaseTitle(), null,
                releaseGroup.releaseGroupId(), releaseArtist.artistId(), resolved.releaseId()));
        final var track = newTrack(trackToIngest, resolved, artist, releaseGroup, release, duration, format);
        trackDao.insert(track);
        return track;
    }

    /**
     * @return the track the ingest produced
     */
    @Transactional
    public Track loadByIngest(final UUID trackIngestId) {
        return trackDao.loadByIngest(trackIngestId);
    }

    private Artist upsertArtist(final ResolvedArtist artist) {
        return artistDao.upsert(new Artist(uuidv7(), artist.name(), null, artist.artistId()));
    }

    private Track newTrack(final TrackToIngest trackToIngest,
                           final ResolvedTrack resolved,
                           final Artist artist,
                           final ReleaseGroup releaseGroup,
                           final Release release,
                           final Duration duration,
                           final Format format) {
        final var trackId = uuidv7();
        return new Track(
                trackId,
                resolved.title(),
                Math.toIntExact(duration.toSeconds()),
                audioFormat(format),
                library.libraryPath(releaseGroup, release, trackId, resolved.title(), format),
                artist.artistId(),
                release.releaseId(),
                trackToIngest.trackIngestId()
        );
    }

    private static AudioFormat audioFormat(final Format format) {
        return switch (format) {
            case FLAC -> AudioFormat.FLAC;
        };
    }
}
