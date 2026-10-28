package org.sona.metadata.resolver;

import static java.util.Comparator.comparingDouble;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.sona.utils.DateUtils.parsePartialDate;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.sona.client.AcoustIdClient;
import org.sona.client.MusicBrainzClient;
import org.sona.client.model.ArtistCredit;
import org.sona.client.model.LabelInfo;
import org.sona.client.model.Media;
import org.sona.client.model.Recording;
import org.sona.client.model.Release;
import org.sona.client.model.acoustid.LookupResult;
import org.sona.client.model.acoustid.RecordingId;
import org.sona.client.model.query.RecordingQuery;
import org.sona.fingerprint.Fingerprint;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
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

    private final MusicBrainzClient client;
    private final AcoustIdClient acoustIdClient;

    @Override
    public TrackResolution resolveTrack(
            final TrackToIngest trackToIngest, final RawMetadata metadata, final Fingerprint fingerprint)
            throws IOException, InterruptedException {
        final var fingerprintMatches = lookUpFingerprint(fingerprint, metadata.duration());
        final var closeMatch = closeMatch(fingerprintMatches, metadata.duration());
        if (closeMatch != null) {
            return new TrackResolution.Matched(convertToTrack(closeMatch));
        }
        // Looser matches only help choose between the recordings the tags find.
        final var matchedRecordingIds =
                recordingIds(fingerprintMatches.stream()).collect(toUnmodifiableSet());
        return resolveFromTags(trackToIngest, metadata, matchedRecordingIds);
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
            final TrackToIngest trackToIngest, final RawMetadata metadata, final Set<UUID> matchedRecordingIds) {
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

        final var albumTitle = metadata.findValues(Tag.RELEASE_TITLE).stream()
                .findFirst()
                .flatMap(TagValue::asString)
                .map(String::strip)
                .orElse(null);

        final var evidence = new TagEvidence(matchedRecordingIds, albumTitle, metadata.duration());

        final var albumArtists =
                metadata.findValues(Tag.ALBUM_ARTIST).stream().findFirst().flatMap(TagValue::asString).stream()
                        .flatMap(this::semicolonSplit) // Picard groups multiple artists into a single value tag.
                        .toList();

        if (artist != null) {
            // If there are other artist credits this is likely classical + tagged by MusicBrainz.
            // We should search by the performers in this case.
            for (final var albumArtist : albumArtists) {
                final var recordingQuery = new RecordingQuery(trackName, albumArtist, trackNumber, -1);

                final var recordingsResponse = client.searchRecordings(recordingQuery);
                final var candidates = rankCandidates(recordingsResponse.recordings(), evidence);
                if (!candidates.isEmpty()) {
                    return resolution(candidates);
                }
            }
        }

        // Typical path just uses track artist directly
        final var recordingQuery = new RecordingQuery(trackName, artist, trackNumber, -1);

        final var recordingsResponse = client.searchRecordings(recordingQuery);
        return resolution(rankCandidates(recordingsResponse.recordings(), evidence));
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
        return recording.releases().stream()
                .map(release -> new TrackCandidate(
                        convertToTrack(recording, release), release.title(), score(recording, release, evidence)))
                .max(comparingInt(TrackCandidate::score))
                .orElseThrow();
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
