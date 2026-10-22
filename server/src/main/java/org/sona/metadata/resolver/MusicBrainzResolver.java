package org.sona.metadata.resolver;

import static org.sona.utils.DateUtils.parsePartialDate;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.sona.client.MusicBrainzClient;
import org.sona.client.model.ArtistCredit;
import org.sona.client.model.LabelInfo;
import org.sona.client.model.Media;
import org.sona.client.model.Recording;
import org.sona.client.model.query.RecordingQuery;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class MusicBrainzResolver implements MetadataResolver {

    private final MusicBrainzClient client;

    @Override
    public Optional<ResolvedTrack> resolveTrack(final TrackToIngest trackToIngest, final RawMetadata metadata)
            throws IOException, InterruptedException {
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
                final var candidate = findCandidate(recordingsResponse.recordings());
                if (candidate != null) {
                    return Optional.of(convertToTrack(candidate));
                }
            }
        }

        // Typical path just uses track artist directly
        final var recordingQuery = new RecordingQuery(trackName, artist, trackNumber, -1);

        final var recordingsResponse = client.searchRecordings(recordingQuery);
        final var candidate = findCandidate(recordingsResponse.recordings());
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

    private Recording findCandidate(final List<Recording> recordings) {
        return recordings.stream()
                // TODO: Implement better selection logic
                // The library path is built from the release, so recordings without one can't be imported.
                .filter(recording ->
                        recording.releases() != null && !recording.releases().isEmpty())
                .findFirst()
                .orElse(null);
    }

    private ResolvedTrack convertToTrack(final Recording recording) {
        // TODO: Pick the release that best matches the album tags
        final var release = recording.releases().getFirst();
        // A search result's release only lists the medium the recording is on, with the tracks before it skipped.
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
