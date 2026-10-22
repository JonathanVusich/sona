package org.sona.library;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.sona.utils.IDGenerator.uuidv5;
import static org.sona.utils.IDGenerator.uuidv7;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sona.db.ArtistDao;
import org.sona.db.IngestGroupDao;
import org.sona.db.LabelDao;
import org.sona.db.MediumDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.ReleaseLabelDao;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stores a few releases for local development, so the library's pages have something to show. Their tracks have no
 * audio files. Each row's MusicBrainz ID is worked out from its name, so every start upserts the rows it stored before
 * rather than adding more.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevLibraryRunner implements ApplicationRunner {

    private static final String VARIOUS_ARTISTS = "Various Artists";

    private static final List<DevArtist> ARTISTS = List.of(
            new DevArtist("Coldplay", "GB"),
            new DevArtist("Keane", "GB"),
            new DevArtist("Blur", "GB"),
            new DevArtist("Pulp", "GB"),
            new DevArtist("Ada Mori", null),
            new DevArtist(VARIOUS_ARTISTS, null));

    private static final DevReleaseGroup PARACHUTES =
            new DevReleaseGroup("Coldplay", "Parachutes", ReleaseGroupType.ALBUM, LocalDate.of(2000, 7, 10));
    private static final DevReleaseGroup HOPES_AND_FEARS =
            new DevReleaseGroup("Keane", "Hopes and Fears", ReleaseGroupType.ALBUM, LocalDate.of(2004, 5, 10));
    private static final DevReleaseGroup BRITPOP_ANTHEMS =
            new DevReleaseGroup(VARIOUS_ARTISTS, "Britpop Anthems", ReleaseGroupType.ALBUM, LocalDate.of(2010, 3, 1));
    private static final DevReleaseGroup FIELD_RECORDINGS =
            new DevReleaseGroup("Ada Mori", "Field Recordings", ReleaseGroupType.EP, null);

    private static final List<DevTrack> HOPES_AND_FEARS_TRACKS = List.of(
            new DevTrack("Somewhere Only We Know", 237, "Keane"),
            new DevTrack("This Is the Last Time", 211, "Keane"),
            new DevTrack("Bend and Break", 216, "Keane"),
            new DevTrack("We Might as Well Be Strangers", 191, "Keane"),
            new DevTrack("Everybody's Changing", 215, "Keane"),
            new DevTrack("Your Eyes Open", 193, "Keane"),
            new DevTrack("She Has No Time", 285, "Keane"),
            new DevTrack("Can't Stop Now", 216, "Keane"),
            new DevTrack("Sunshine", 252, "Keane"),
            new DevTrack("Untitled 1", 337, "Keane"),
            new DevTrack("Bedshaped", 278, "Keane"));

    private static final List<DevRelease> RELEASES = List.of(
            new DevRelease(
                    PARACHUTES,
                    "Parachutes",
                    LocalDate.of(2000, 7, 10),
                    "GB",
                    List.of("Parlophone"),
                    List.of(new DevMedium(
                            "CD",
                            List.of(
                                    new DevTrack("Don't Panic", 137, "Coldplay"),
                                    new DevTrack("Shiver", 304, "Coldplay"),
                                    new DevTrack("Spies", 318, "Coldplay"),
                                    new DevTrack("Sparks", 227, "Coldplay"),
                                    new DevTrack("Yellow", 269, "Coldplay"),
                                    new DevTrack("Trouble", 273, "Coldplay"),
                                    new DevTrack("Parachutes", 46, "Coldplay"),
                                    new DevTrack("High Speed", 254, "Coldplay"),
                                    new DevTrack("We Never Change", 249, "Coldplay"),
                                    new DevTrack("Everything's Not Lost", 435, "Coldplay"))))),
            new DevRelease(
                    HOPES_AND_FEARS,
                    "Hopes and Fears",
                    LocalDate.of(2004, 5, 10),
                    "GB",
                    List.of("Island"),
                    List.of(new DevMedium("CD", HOPES_AND_FEARS_TRACKS))),
            // A second edition of the same release group, with a disc of B-sides.
            new DevRelease(
                    HOPES_AND_FEARS,
                    "Hopes and Fears (Deluxe Edition)",
                    LocalDate.of(2014, 5, 12),
                    "GB",
                    List.of("Island"),
                    List.of(
                            new DevMedium("CD", HOPES_AND_FEARS_TRACKS),
                            new DevMedium(
                                    "CD",
                                    List.of(
                                            new DevTrack("Snowed Under", 230, "Keane"),
                                            new DevTrack("Allemande", 247, "Keane"),
                                            new DevTrack("To the End of the Earth", 226, "Keane"),
                                            new DevTrack("Fly to Me", 199, "Keane"))))),
            // A compilation, whose tracks each have their own artist, on two discs.
            new DevRelease(
                    BRITPOP_ANTHEMS,
                    "Britpop Anthems",
                    LocalDate.of(2010, 3, 1),
                    "GB",
                    List.of("Parlophone", "EMI"),
                    List.of(
                            new DevMedium(
                                    "CD",
                                    List.of(
                                            new DevTrack("Yellow", 269, "Coldplay"),
                                            new DevTrack("Parklife", 185, "Blur"),
                                            new DevTrack("Common People", 350, "Pulp"))),
                            new DevMedium(
                                    "CD",
                                    List.of(
                                            new DevTrack("Somewhere Only We Know", 237, "Keane"),
                                            new DevTrack("Song 2", 122, "Blur"),
                                            new DevTrack("Disco 2000", 273, "Pulp"))))),
            // A release MusicBrainz knows little about: no date, country, label or format.
            new DevRelease(
                    FIELD_RECORDINGS,
                    "Field Recordings",
                    null,
                    null,
                    List.of(),
                    List.of(new DevMedium(
                            null,
                            List.of(
                                    new DevTrack("Morning", 180, "Ada Mori"),
                                    new DevTrack("Rain", 240, "Ada Mori"))))));

    private final ArtistDao artistDao;
    private final ReleaseGroupDao releaseGroupDao;
    private final ReleaseDao releaseDao;
    private final LabelDao labelDao;
    private final ReleaseLabelDao releaseLabelDao;
    private final MediumDao mediumDao;
    private final TrackDao trackDao;
    private final IngestGroupDao ingestGroupDao;
    private final TrackToIngestDao trackToIngestDao;

    @Override
    public void run(final ApplicationArguments args) {
        final var artists = upsertArtists();
        for (final var devRelease : RELEASES) {
            final var release = storeRelease(devRelease, artists);
            log.info(
                    "Dev release {} is at /releases/{}, in /release-groups/{}",
                    release.name(),
                    release.releaseId(),
                    release.releaseGroupId());
        }
    }

    private Map<String, Artist> upsertArtists() {
        return ARTISTS.stream()
                .map(artist -> artistDao.upsert(new Artist(
                        uuidv7(), artist.name(), artist.country(), null, 0, musicBrainzId("artist", artist.name()))))
                .collect(toUnmodifiableMap(Artist::name, Function.identity()));
    }

    private Release storeRelease(final DevRelease devRelease, final Map<String, Artist> artists) {
        final var devGroup = devRelease.releaseGroup();
        final var artistId = artists.get(devGroup.artist()).artistId();
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                devGroup.name(),
                devGroup.type(),
                devGroup.firstReleaseDate(),
                artistId,
                0,
                musicBrainzId("release-group", devGroup.name())));
        final var release = releaseDao.upsert(new Release(
                uuidv7(),
                devRelease.name(),
                null,
                devRelease.date(),
                devRelease.country(),
                0,
                releaseGroup.releaseGroupId(),
                artistId,
                musicBrainzId("release", devRelease.name())));
        upsertLabels(release, devRelease.labels());
        final var media = upsertMedia(release, devRelease.media());
        // Tracks are only inserted, so a release that has them was stored by an earlier start.
        if (release.trackCount() == 0) {
            insertTracks(devRelease.media(), media, artists);
        }
        return release;
    }

    private void upsertLabels(final Release release, final List<String> names) {
        final var labels = labelDao.upsertAll(names.stream()
                .map(name -> new Label(uuidv7(), name, musicBrainzId("label", name)))
                .toList());
        releaseLabelDao.upsertAll(labels.stream()
                .map(label -> new ReleaseLabel(release.releaseId(), label.labelId()))
                .toList());
    }

    /**
     * @return the stored media in order, as the upsert returns them in none
     */
    private List<Medium> upsertMedia(final Release release, final List<DevMedium> devMedia) {
        final var media = IntStream.range(0, devMedia.size())
                .mapToObj(index -> new Medium(
                        uuidv7(),
                        release.releaseId(),
                        index + 1,
                        devMedia.get(index).format()))
                .toList();
        final var stored = mediumDao.upsertAll(media);
        return stored.stream().sorted(comparingInt(Medium::position)).toList();
    }

    private void insertTracks(
            final List<DevMedium> devMedia, final List<Medium> media, final Map<String, Artist> artists) {
        for (var mediumIndex = 0; mediumIndex < devMedia.size(); mediumIndex++) {
            final var tracks = devMedia.get(mediumIndex).tracks();
            final var mediumId = media.get(mediumIndex).mediumId();
            for (var trackIndex = 0; trackIndex < tracks.size(); trackIndex++) {
                final var devTrack = tracks.get(trackIndex);
                insertTrack(devTrack, mediumId, trackIndex + 1, artists.get(devTrack.artist()));
            }
        }
    }

    private void insertTrack(final DevTrack devTrack, final UUID mediumId, final int position, final Artist artist) {
        final var trackId = uuidv7();
        final var fileName = trackId + ".flac";
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads");
        ingestGroupDao.upsert(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), fileName, IngestState.COMPLETED);
        trackToIngestDao.insert(ingest);
        trackDao.insert(new Track(
                trackId,
                devTrack.name(),
                devTrack.duration(),
                AudioFormat.FLAC,
                fileName,
                mediumId,
                position,
                artist.artistId(),
                ingest.trackIngestId()));
    }

    private static UUID musicBrainzId(final String kind, final String name) {
        return uuidv5("sona-dev:" + kind + ":" + name);
    }

    private record DevArtist(String name, String country) {}

    private record DevReleaseGroup(String artist, String name, ReleaseGroupType type, LocalDate firstReleaseDate) {}

    private record DevRelease(
            DevReleaseGroup releaseGroup,
            String name,
            LocalDate date,
            String country,
            List<String> labels,
            List<DevMedium> media) {}

    private record DevMedium(String format, List<DevTrack> tracks) {}

    /**
     * @param duration in seconds
     */
    private record DevTrack(String name, int duration, String artist) {}
}
