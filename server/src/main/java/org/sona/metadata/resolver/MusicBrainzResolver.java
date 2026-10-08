package org.sona.metadata.resolver;

import lombok.RequiredArgsConstructor;
import org.sona.client.MusicBrainzClient;
import org.sona.client.model.Artist;
import org.sona.client.model.Recording;
import org.sona.client.model.query.RecordingQuery;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public final class MusicBrainzResolver implements MetadataResolver {

    private final MusicBrainzClient client;

    @Override
    public Optional<ResolvedTrack> resolveTrack(final TrackToIngest trackToIngest, final RawMetadata metadata) throws IOException, InterruptedException {
        final int trackNumber = metadata.search(Tag.TRACK_NUMBER).stream()
                .findFirst()
                .flatMap(this::toInt)
                .orElse(-1);

        final var trackName = metadata.search(Tag.TRACK_TITLE).stream()
                .findFirst()
                .flatMap(this::toStr)
                .orElse(trackToIngest.originalFileName());

        final var artist = metadata.search(Tag.TRACK_ARTIST).stream()
                .findFirst()
                .flatMap(this::toStr)
                .orElse(null);

        final var albumArtists = metadata.search(Tag.ALBUM_ARTIST).stream()
                .findFirst()
                .flatMap(this::toStr)
                .stream()
                .flatMap(this::semicolonSplit) // Picard groups multiple artists into a single value tag.
                .toList();

        if (artist != null) {
            // If there are other artist credits this is likely classical + tagged by MusicBrainz.
            // We should search by the performers in this case.
            for (final var albumArtist: albumArtists) {
                final var recordingQuery = new RecordingQuery(
                        trackName,
                        albumArtist,
                        trackNumber,
                        -1
                );

                final var recordingsResponse = client.searchRecordings(recordingQuery);
                final var candidate = findCandidate(recordingsResponse.recordings());
                if (candidate != null) {
                    return Optional.of(convertToTrack(candidate));
                }
            }
        }

        // Typical path just uses track artist directly
        final var recordingQuery = new RecordingQuery(
                trackName,
                artist,
                trackNumber,
                -1
        );

        final var recordingsResponse = client.searchRecordings(recordingQuery);
        final var candidate = findCandidate(recordingsResponse.recordings());
        if (candidate != null) {
            return Optional.of(convertToTrack(candidate));
        } else {
            return Optional.empty();
        }
    }

    private Recording findCandidate(final List<Recording> recordings) {
        return recordings.stream()
                // TODO: Implement better selection logic
                // The library path is built from the release, so recordings without one can't be imported.
                .filter(recording -> recording.releases() != null && !recording.releases().isEmpty())
                .findFirst()
                .orElse(null);
    }

    private ResolvedTrack convertToTrack(final Recording recording) {
        // TODO: Pick the release that best matches the album tags
        final var release = recording.releases().getFirst();
        // TODO: Keep every credited artist, not just the first (e.g. "feat." credits)
        final var artist = recording.artistCredit().getFirst().artist();
        final var releaseArtist = release.artistCredit().getFirst().artist();
        return new ResolvedTrack(
                recording.id(),
                recording.title(),
                resolvedArtist(artist),
                release.id(),
                release.title(),
                resolvedArtist(releaseArtist),
                release.releaseGroup().id(),
                release.releaseGroup().title()
        );
    }

    private static ResolvedArtist resolvedArtist(final Artist artist) {
        return new ResolvedArtist(artist.id(), artist.name());
    }

    private Optional<String> toStr(final TagValue tagValue) {
        return switch (tagValue) {
            case TagValue.Str(String val) -> Optional.of(val);
            default -> Optional.empty();
        };
    }

    private Stream<String> semicolonSplit(final String tagValue) {
        return Arrays.stream(tagValue.split(";"))
                .map(String::strip);
    }

    private Optional<Integer> toInt(final TagValue tagValue) {
        return switch (tagValue) {
            case TagValue.Int(int val) -> Optional.of(val);
            default -> Optional.empty();
        };
    }
}
