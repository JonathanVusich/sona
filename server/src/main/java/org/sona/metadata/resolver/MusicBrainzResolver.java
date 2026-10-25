package org.sona.metadata.resolver;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.sona.utils.DateUtils.parsePartialDate;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    private final MusicBrainzClient client;
    private final AcoustIdClient acoustIdClient;

    @Override
    public Optional<ResolvedTrack> resolveTrack(
            final TrackToIngest trackToIngest, final RawMetadata metadata, final Fingerprint fingerprint)
            throws IOException, InterruptedException {
        final var fingerprintMatches = lookUpFingerprint(fingerprint, metadata.duration());
        final var closeMatch = closeMatch(fingerprintMatches);
        if (closeMatch != null) {
            return Optional.of(convertToTrack(closeMatch));
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
     * imported
     */
    private Recording closeMatch(final List<LookupResult> fingerprintMatches) {
        final var closeMatches = fingerprintMatches.stream().filter(result -> result.score() >= CLOSE_MATCH_SCORE);
        // Each recording is looked up only if those of closer matches can't be imported.
        return recordingIds(closeMatches)
                .map(client::lookupRecording)
                .filter(MusicBrainzResolver::hasRelease)
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

    private Optional<ResolvedTrack> resolveFromTags(
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
                final var candidate = findCandidate(recordingsResponse.recordings(), matchedRecordingIds);
                if (candidate != null) {
                    return Optional.of(convertToTrack(candidate));
                }
            }
        }

        // Typical path just uses track artist directly
        final var recordingQuery = new RecordingQuery(trackName, artist, trackNumber, -1);

        final var recordingsResponse = client.searchRecordings(recordingQuery);
        final var candidate = findCandidate(recordingsResponse.recordings(), matchedRecordingIds);
        if (candidate != null) {
            return Optional.of(convertToTrack(candidate));
        } else {
            return Optional.empty();
        }
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

    private Recording findCandidate(final List<Recording> recordings, final Set<UUID> matchedRecordingIds) {
        final var importable =
                recordings.stream().filter(MusicBrainzResolver::hasRelease).toList();
        // TODO: Implement better selection logic
        return importable.stream()
                .filter(recording -> matchedRecordingIds.contains(recording.id()))
                .findFirst()
                .or(() -> importable.stream().findFirst())
                .orElse(null);
    }

    // The library path is built from the release, so recordings without one can't be imported.
    private static boolean hasRelease(final Recording recording) {
        return recording.releases() != null && !recording.releases().isEmpty();
    }

    private ResolvedTrack convertToTrack(final Recording recording) {
        // TODO: Pick the release that best matches the album tags
        final var release = recording.releases().getFirst();
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
