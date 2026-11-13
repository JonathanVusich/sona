package org.sona.metadata.resolver;

import static java.util.Comparator.comparingDouble;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.comparingLong;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.sona.metadata.resolver.TrackPlacer.placeFiles;
import static org.sona.utils.DateUtils.parsePartialDate;

import dev.javax.sonaudio.fingerprint.Fingerprint;
import dev.javax.sonaudio.metadata.RawMetadata;
import dev.javax.sonaudio.metadata.Tag;
import dev.javax.sonaudio.metadata.TagValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.sona.client.AcoustIdClient;
import org.sona.client.MusicBrainzClient;
import org.sona.client.model.ArtistCredit;
import org.sona.client.model.LabelInfo;
import org.sona.client.model.Media;
import org.sona.client.model.Recording;
import org.sona.client.model.Release;
import org.sona.client.model.Track;
import org.sona.client.model.acoustid.LookupResult;
import org.sona.client.model.acoustid.RecordingId;
import org.sona.client.model.query.RecordingQuery;
import org.sona.metadata.resolver.TrackPlacer.FileEvidence;
import org.sona.metadata.resolver.TrackPlacer.FilePlacement;
import org.sona.metadata.resolver.TrackPlacer.ReleaseTrack;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class MusicBrainzResolver implements MetadataResolver {

    // AcoustID scores matches from 0 to 1. From this score on, the fingerprints are of the same audio.
    private static final double CLOSE_MATCH_SCORE = 0.9;

    // MusicBrainz scores search results from 0 to 100. With the adjustments below, a recording from this score on is
    // imported without review.
    private static final int ACCEPT_SCORE = 90;
    // A recording AcoustID matched loosely is likelier to be the right one of those the tags find.
    private static final int LOOSE_MATCH_BONUS = 10;
    // The release isn't the one the album tag names.
    private static final int ALBUM_MISMATCH_PENALTY = 30;
    // The recording's length is more than the tolerance from the track's duration, or isn't known. Enough that even a
    // perfect search score (100) with the bonus above stays under ACCEPT_SCORE.
    private static final int DURATION_MISMATCH_PENALTY = 50;
    // How far a recording's length may be from the duration of the audio for it to be the same recording.
    private static final Duration DURATION_TOLERANCE = Duration.ofSeconds(3);
    private static final int MAX_CANDIDATES = 5;
    // How many of the releases an album's files were found on are looked up, at most, for one with as many tracks as
    // the album has files. Each lookup takes a second of MusicBrainz's rate limit.
    private static final int MAX_RELEASE_LOOKUPS = 10;
    // What a recording the audio matched closely scores on each of its releases, as a candidate.
    private static final int CLOSE_MATCH_CANDIDATE_SCORE = 100 + LOOSE_MATCH_BONUS;

    private final MusicBrainzClient client;
    private final AcoustIdClient acoustIdClient;

    @Override
    public TrackResolution resolveTrack(
            final TrackToIngest trackToIngest,
            final RawMetadata metadata,
            final Duration duration,
            final Fingerprint fingerprint) {
        final var fingerprintMatches = lookUpFingerprint(fingerprint, duration);
        final var closeMatch = closeMatch(fingerprintMatches, duration);
        if (closeMatch != null) {
            return new TrackResolution.Matched(convertToTrack(closeMatch));
        }
        // Looser matches only help choose between the recordings the tags find.
        final var matchedRecordingIds =
                recordingIds(fingerprintMatches.stream()).collect(toUnmodifiableSet());
        return resolveFromTags(trackToIngest, metadata, duration, matchedRecordingIds);
    }

    @Override
    public AlbumResolution resolveAlbum(final List<AlbumTrack> tracks) {
        final var fileCount = tracks.size();
        final var matches = new ArrayList<FileMatches>();
        for (final var track : tracks) {
            matches.add(fileMatches(track));
        }
        final var evidence = matches.stream().map(FileMatches::evidence).toList();
        final var candidates = new ArrayList<ReleaseCandidate>();
        final var releaseIds = likelyReleases(matches, fileCount);
        for (final var releaseId : releaseIds) {
            final var release = client.lookupRelease(releaseId);
            final var placement = bestPlacement(release, evidence, fileCount).orElse(null);
            if (placement == null) {
                continue;
            }
            final var matched = placement.matchedTracks(release, evidence).orElse(null);
            if (matched != null) {
                return new AlbumResolution.Matched(matched);
            }
            candidates.add(placement.candidate(release));
            if (candidates.size() == MAX_CANDIDATES) {
                break;
            }
        }
        return new AlbumResolution.NeedsReview(rankReleases(candidates));
    }

    private FileMatches fileMatches(final AlbumTrack track) {
        final var fingerprintMatches = lookUpFingerprint(track.fingerprint(), track.duration());
        final var matchedRecordingIds =
                recordingIds(fingerprintMatches.stream()).collect(toUnmodifiableSet());
        final var closeMatch = closeMatch(fingerprintMatches, track.duration());
        if (closeMatch != null) {
            return closeMatchFile(track, closeMatch);
        }
        final var tagEvidence = new TagEvidence(matchedRecordingIds, albumTitle(track.metadata()), track.duration());
        final var recordings = searchRecordings(track.trackToIngest(), track.metadata());
        // A loose fingerprint match places a file only through the score it adds to a recording the tags found, as it
        // only helps choose between those for a file on its own.
        final var recordingIds = recordings.stream()
                .flatMap(recording -> candidatesOnEachRelease(recording, tagEvidence).stream())
                .filter(candidate -> candidate.score() >= ACCEPT_SCORE)
                .map(candidate -> candidate.track().recordingId())
                .collect(toUnmodifiableSet());
        final var releases = recordings.stream()
                .flatMap(recording -> recording.releases().stream())
                .toList();
        return new FileMatches(fileEvidence(track, recordingIds), releases, rankCandidates(recordings, tagEvidence));
    }

    // Audio that matched closely is the recording on every release it is on.
    private FileMatches closeMatchFile(final AlbumTrack track, final Recording recording) {
        final var recordingIds = Set.of(recording.id());
        final var candidate = new TrackCandidate(
                convertToTrack(recording), recording.releases().getFirst().title(), CLOSE_MATCH_CANDIDATE_SCORE);
        return new FileMatches(fileEvidence(track, recordingIds), recording.releases(), List.of(candidate));
    }

    private static FileEvidence fileEvidence(final AlbumTrack track, final Set<UUID> recordingIds) {
        final var metadata = track.metadata();
        return new FileEvidence(
                recordingIds,
                intTag(metadata, Tag.DISC_NUMBER),
                intTag(metadata, Tag.TRACK_NUMBER),
                metadata.findValues(Tag.TRACK_TITLE).stream()
                        .findFirst()
                        .flatMap(TagValue::asString)
                        .orElse(null),
                track.duration());
    }

    private static Integer intTag(final RawMetadata metadata, final Tag tag) {
        return metadata.findValues(tag).stream()
                .findFirst()
                .flatMap(TagValue::asInt)
                .orElse(null);
    }

    /**
     * @return the releases that might have as many tracks as the album has files, those most of its files were found
     * on first, then those with the better matches, then in the order MusicBrainz listed them
     */
    private static List<UUID> likelyReleases(final List<FileMatches> matches, final int fileCount) {
        final var filesByRelease = matches.stream()
                .flatMap(file -> file.releases().stream()
                        .filter(release -> mayHaveTrackCount(release, fileCount))
                        .map(Release::id)
                        .distinct())
                .collect(groupingBy(Function.identity(), LinkedHashMap::new, counting()));
        final var scoreByRelease = matches.stream()
                .flatMap(file -> file.ranked().stream())
                .collect(groupingBy(
                        candidate -> candidate.track().releaseId(),
                        LinkedHashMap::new,
                        summingInt(TrackCandidate::score)));
        final Comparator<UUID> byFileCount = comparingLong(filesByRelease::get);
        final Comparator<UUID> byScore = comparingInt(releaseId -> scoreByRelease.getOrDefault(releaseId, 0));
        return filesByRelease.keySet().stream()
                .sorted(byFileCount.thenComparing(byScore).reversed())
                .limit(MAX_RELEASE_LOOKUPS)
                .toList();
    }

    // Search results give a release's track count, and the medium the recording is on; lookups only the medium.
    private static boolean mayHaveTrackCount(final Release release, final int fileCount) {
        final var trackCount = release.trackCount();
        if (trackCount == null || trackCount == fileCount) {
            return true;
        }
        return release.media().stream().anyMatch(medium -> medium.trackCount() == fileCount);
    }

    /**
     * @return where the files are placed on the part of the release that places the most, of those with as many
     * tracks as the album has files: all of it, or one of its media; empty when no part has as many
     */
    private static Optional<ScopedPlacement> bestPlacement(
            final Release release, final List<FileEvidence> evidence, final int fileCount) {
        return scopes(release, fileCount).stream()
                .map(scope -> new ScopedPlacement(scope, placeFiles(scopeTracks(release, scope), evidence)))
                .max(comparingLong(ScopedPlacement::placedCount));
    }

    private static List<ReleaseScope> scopes(final Release release, final int fileCount) {
        final var media = release.media();
        final var wholeRelease = Stream.<ReleaseScope>of(new ReleaseScope.WholeRelease())
                .filter(_ -> media.stream().mapToInt(Media::trackCount).sum() == fileCount);
        // A release of one medium is that medium.
        final var oneMedium = media.stream()
                .filter(medium -> media.size() > 1 && medium.trackCount() == fileCount)
                .<ReleaseScope>map(medium -> new ReleaseScope.OneMedium(medium.position()));
        return Stream.concat(wholeRelease, oneMedium).toList();
    }

    private static List<ReleaseTrack> scopeTracks(final Release release, final ReleaseScope scope) {
        final var media = release.media().stream().filter(medium -> switch (scope) {
            case ReleaseScope.WholeRelease _ -> true;
            case ReleaseScope.OneMedium one -> medium.position() == one.position();
        });
        return media.flatMap(medium ->
                        mediumTracks(medium).stream().map(track -> new ReleaseTrack(medium.position(), track)))
                .toList();
    }

    // MusicBrainz may not know a medium's tracklist.
    private static List<Track> mediumTracks(final Media medium) {
        if (medium.tracks() == null) {
            return List.of();
        }
        return medium.tracks();
    }

    // Stable, so releases that place as many files keep their order.
    private static List<ReleaseCandidate> rankReleases(final List<ReleaseCandidate> candidates) {
        return candidates.stream()
                .sorted(comparingInt(ReleaseCandidate::placedCount).reversed())
                .toList();
    }

    private List<LookupResult> lookUpFingerprint(final Fingerprint fingerprint, final Duration duration) {
        if (fingerprint.isEmpty()) {
            return List.of();
        }
        return acoustIdClient.lookup(fingerprint.compressed(), duration).results();
    }

    /**
     * @return the recording of the closest match, or null when no match is close or none of their recordings can be
     * imported or is as long as the audio
     */
    private Recording closeMatch(final List<LookupResult> fingerprintMatches, final Duration duration) {
        final var closeMatches = fingerprintMatches.stream().filter(result -> result.score() >= CLOSE_MATCH_SCORE);
        // Each recording is looked up only if those of closer matches can't be imported.
        return recordingIds(closeMatches)
                .map(client::lookupRecording)
                .filter(MusicBrainzResolver::hasRelease)
                .filter(recording -> hasDuration(recording, duration))
                .findFirst()
                .orElse(null);
    }

    // The recordings of the closest matches first, each once.
    private static Stream<UUID> recordingIds(final Stream<LookupResult> fingerprintMatches) {
        return fingerprintMatches
                .sorted(comparingDouble(LookupResult::score).reversed())
                .flatMap(MusicBrainzResolver::linkedRecordings)
                .map(RecordingId::id)
                .distinct();
    }

    private static Stream<RecordingId> linkedRecordings(final LookupResult fingerprintMatch) {
        // An AcoustID track may not be linked to a recording yet.
        if (fingerprintMatch.recordings() == null) {
            return Stream.empty();
        }
        return fingerprintMatch.recordings().stream();
    }

    private TrackResolution resolveFromTags(
            final TrackToIngest trackToIngest,
            final RawMetadata metadata,
            final Duration duration,
            final Set<UUID> matchedRecordingIds) {
        final var evidence = new TagEvidence(matchedRecordingIds, albumTitle(metadata), duration);
        final var recordings = searchRecordings(trackToIngest, metadata);
        return resolution(rankCandidates(recordings, evidence));
    }

    private static String albumTitle(final RawMetadata metadata) {
        return metadata.findValues(Tag.RELEASE_TITLE).stream()
                .findFirst()
                .flatMap(TagValue::asString)
                .map(String::strip)
                .orElse(null);
    }

    /**
     * @return the importable recordings the tags find
     */
    private List<Recording> searchRecordings(final TrackToIngest trackToIngest, final RawMetadata metadata) {
        final int trackNumber = metadata.findValues(Tag.TRACK_NUMBER).stream()
                .findFirst()
                .flatMap(TagValue::asInt)
                .orElse(-1);

        final var trackName = metadata.findValues(Tag.TRACK_TITLE).stream()
                .findFirst()
                .flatMap(TagValue::asString)
                .orElse(trackToIngest.originalFileName());

        final var artist = metadata.findValues(Tag.TRACK_ARTIST).stream()
                .findFirst()
                .flatMap(TagValue::asString)
                .orElse(null);

        final var albumArtists =
                metadata.findValues(Tag.ALBUM_ARTIST).stream().findFirst().flatMap(TagValue::asString).stream()
                        .flatMap(this::semicolonSplit) // Picard groups multiple artists into a single value tag.
                        .toList();

        if (artist != null) {
            // If there are other artist credits this is likely classical + tagged by MusicBrainz.
            // We should search by the performers in this case.
            for (final var albumArtist : albumArtists) {
                final var recordingQuery = new RecordingQuery(trackName, albumArtist, trackNumber, -1);

                final var recordings =
                        importable(client.searchRecordings(recordingQuery).recordings());
                if (!recordings.isEmpty()) {
                    return recordings;
                }
            }
        }

        // Typical path just uses track artist directly
        final var recordingQuery = new RecordingQuery(trackName, artist, trackNumber, -1);

        return importable(client.searchRecordings(recordingQuery).recordings());
    }

    private static List<Recording> importable(final List<Recording> recordings) {
        return recordings.stream().filter(MusicBrainzResolver::hasRelease).toList();
    }

    private static TrackResolution resolution(final List<TrackCandidate> candidates) {
        if (!candidates.isEmpty() && candidates.getFirst().score() >= ACCEPT_SCORE) {
            return new TrackResolution.Matched(candidates.getFirst().track());
        }
        return new TrackResolution.NeedsReview(candidates);
    }

    @Override
    public ResolvedRelease resolveRelease(final UUID releaseId) {
        final var release = client.lookupRelease(releaseId);
        final var releaseGroup = release.releaseGroup();
        return new ResolvedRelease(
                release.id(),
                release.title(),
                parsePartialDate(release.date()),
                release.country(),
                labels(release.labelInfo()),
                media(release.media()),
                firstArtist(release.artistCredit()),
                new ResolvedReleaseGroup(
                        releaseGroup.id(),
                        releaseGroup.title(),
                        releaseGroupType(releaseGroup.primaryType()),
                        parsePartialDate(releaseGroup.firstReleaseDate()),
                        firstArtist(releaseGroup.artistCredit())));
    }

    @Override
    public ResolvedArtist resolveArtist(final UUID artistId) {
        return new ResolvedArtist(client.lookupArtist(artistId));
    }

    /**
     * @return the importable recordings, best first (those of equal score in the order MusicBrainz found them), each
     * on the release that best suits the album tag
     */
    private List<TrackCandidate> rankCandidates(final List<Recording> recordings, final TagEvidence evidence) {
        return recordings.stream()
                .filter(MusicBrainzResolver::hasRelease)
                .map(recording -> bestCandidate(recording, evidence))
                .sorted(comparingInt(TrackCandidate::score).reversed())
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private TrackCandidate bestCandidate(final Recording recording, final TagEvidence evidence) {
        return candidatesOnEachRelease(recording, evidence).stream()
                .max(comparingInt(TrackCandidate::score))
                .orElseThrow();
    }

    private List<TrackCandidate> candidatesOnEachRelease(final Recording recording, final TagEvidence evidence) {
        return recording.releases().stream()
                .map(release -> new TrackCandidate(
                        convertToTrack(recording, release), release.title(), score(recording, release, evidence)))
                .toList();
    }

    private static int score(final Recording recording, final Release release, final TagEvidence evidence) {
        var score = recording.score();
        if (evidence.matchedRecordingIds().contains(recording.id())) {
            score += LOOSE_MATCH_BONUS;
        }
        final var albumTitle = evidence.albumTitle();
        if (albumTitle != null && !albumTitle.equalsIgnoreCase(release.title())) {
            score -= ALBUM_MISMATCH_PENALTY;
        }
        if (!hasDuration(recording, evidence.duration())) {
            score -= DURATION_MISMATCH_PENALTY;
        }
        return score;
    }

    // MusicBrainz knows a recording's length in milliseconds, but not always.
    private static boolean hasDuration(final Recording recording, final Duration duration) {
        if (recording.length() == null) {
            return false;
        }
        final var difference =
                Duration.ofMillis(recording.length()).minus(duration).abs();
        return difference.compareTo(DURATION_TOLERANCE) <= 0;
    }

    /**
     * What a file's tags and audio say about the recording it is, to choose between the recordings a search finds.
     *
     * @param matchedRecordingIds recordings AcoustID matched the audio to, loosely
     * @param albumTitle          the album tag, or null when the file has none
     * @param duration            of the decoded audio
     */
    private record TagEvidence(Set<UUID> matchedRecordingIds, String albumTitle, Duration duration) {}

    /**
     * What was found for a file of an album.
     *
     * @param releases the releases of the recordings it might be, those of its better matches first
     * @param ranked   its best candidates, whose scores rank the releases
     */
    private record FileMatches(FileEvidence evidence, List<Release> releases, List<TrackCandidate> ranked) {}

    /**
     * Where an album's files are placed on a part of a release.
     *
     * @param placements in the order the files were given
     */
    private record ScopedPlacement(ReleaseScope scope, List<FilePlacement> placements) {

        private long placedCount() {
            return placements.stream()
                    .filter(placement -> switch (placement) {
                        case FilePlacement.Placed _ -> true;
                        case FilePlacement.Unplaced _ -> false;
                    })
                    .count();
        }

        /**
         * @return each file's track, when every file is placed on a track as long as its audio; empty otherwise
         */
        private Optional<List<ResolvedTrack>> matchedTracks(final Release release, final List<FileEvidence> evidence) {
            final var tracks = IntStream.range(0, placements.size())
                    .mapToObj(file -> switch (placements.get(file)) {
                        case FilePlacement.Placed placed
                        when placed.track().hasLength(evidence.get(file).duration()) ->
                            Stream.of(resolvedTrack(release, placed.track()));
                        case FilePlacement.Placed _, FilePlacement.Unplaced _ -> Stream.<ResolvedTrack>empty();
                    })
                    .flatMap(Function.identity())
                    .toList();
            if (tracks.size() < placements.size()) {
                return Optional.empty();
            }
            return Optional.of(tracks);
        }

        private ReleaseCandidate candidate(final Release release) {
            return new ReleaseCandidate(
                    release.id(),
                    release.title(),
                    firstArtist(release.artistCredit()),
                    parsePartialDate(release.date()),
                    release.country(),
                    scope,
                    (int) placedCount());
        }
    }

    private static ResolvedTrack resolvedTrack(final Release release, final ReleaseTrack releaseTrack) {
        final var track = releaseTrack.track();
        final var recording = track.recording();
        return new ResolvedTrack(
                recording.id(),
                recording.title(),
                firstArtist(track.artistCredit()),
                release.id(),
                releaseTrack.discNumber(),
                track.position());
    }

    // The library path is built from the release, so recordings without one can't be imported.
    private static boolean hasRelease(final Recording recording) {
        return recording.releases() != null && !recording.releases().isEmpty();
    }

    private ResolvedTrack convertToTrack(final Recording recording) {
        return convertToTrack(recording, recording.releases().getFirst());
    }

    private ResolvedTrack convertToTrack(final Recording recording, final Release release) {
        // A recording's release, searched for or looked up, only lists the medium the recording is on, with the tracks
        // before it skipped.
        final var medium = release.media().getFirst();
        return new ResolvedTrack(
                recording.id(),
                recording.title(),
                firstArtist(recording.artistCredit()),
                release.id(),
                medium.position(),
                medium.trackOffset() + 1);
    }

    // TODO: Keep every credited artist, not just the first (e.g. "feat." credits)
    private static ResolvedArtist firstArtist(final List<ArtistCredit> credits) {
        return new ResolvedArtist(credits.getFirst().artist());
    }

    /**
     * A label is listed once per catalog number, and left out when only the catalog number is known.
     */
    private static List<ResolvedLabel> labels(final List<LabelInfo> labelInfo) {
        return labelInfo.stream()
                .map(LabelInfo::label)
                .filter(Objects::nonNull)
                .map(label -> new ResolvedLabel(label.id(), label.name()))
                .distinct()
                .toList();
    }

    private static List<ResolvedMedium> media(final List<Media> media) {
        return media.stream()
                .map(medium -> new ResolvedMedium(medium.position(), medium.format()))
                .toList();
    }

    private static ReleaseGroupType releaseGroupType(final String primaryType) {
        if (primaryType == null) {
            return null;
        }
        return switch (primaryType) {
            case "Album" -> ReleaseGroupType.ALBUM;
            case "Single" -> ReleaseGroupType.SINGLE;
            case "EP" -> ReleaseGroupType.EP;
            case "Broadcast" -> ReleaseGroupType.BROADCAST;
            case "Other" -> ReleaseGroupType.OTHER;
            default -> throw new IllegalArgumentException("Unknown release group type " + primaryType);
        };
    }

    private Stream<String> semicolonSplit(final String tagValue) {
        return Arrays.stream(tagValue.split(";")).map(String::strip);
    }
}
