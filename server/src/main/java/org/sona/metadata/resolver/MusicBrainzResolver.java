package org.sona.metadata.resolver;

import lombok.RequiredArgsConstructor;
import org.sona.client.MusicBrainzClient;
import org.sona.client.model.Recording;
import org.sona.client.model.query.RecordingQuery;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.sona.utils.IDGenerator.uuidv7;

@Service
@RequiredArgsConstructor
public final class MusicBrainzResolver implements MetadataResolver {

    private final MusicBrainzClient client;

    @Override
    public Optional<Track> resolveTrack(final TrackToIngest trackToIngest, final RawMetadata metadata) throws IOException, InterruptedException {
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
                .findFirst()
                .orElse(null);
    }

    private Track convertToTrack(final Recording recording) {
        // TODO: Create full track data (maybe fetching the full recording using the API)
        return new Track(
                uuidv7(),
                recording.title(),
                null,
                null,
                null,
                null,
                null
        );
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
