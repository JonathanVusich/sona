package org.sona.metadata.resolver;

import java.time.Duration;
import java.util.BitSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import org.sona.client.model.Track;

/**
 * Places an album's files on the tracks of a release, each file on a different track.
 */
@UtilityClass
public class TrackPlacer {

    // How far a track's length may be from the duration of the audio for it to be the same recording.
    private static final Duration LENGTH_TOLERANCE = Duration.ofSeconds(3);

    /**
     * The evidence is weighed strongest first: each file is placed on a track of a recording its audio or tags were
     * found to be, then on the track its disc and track number tags name, if its length or title agrees, then on a
     * track of its title and length.
     *
     * @return where each file is placed, in the order the files were given
     */
    public static List<FilePlacement> placeFiles(final List<ReleaseTrack> tracks, final List<FileEvidence> files) {
        final var discs = discs(tracks);
        final var trackOfFile = new int[files.size()];
        final var placedFiles = new BitSet(files.size());
        final var takenTracks = new BitSet(tracks.size());
        for (final var pass : Pass.values()) {
            for (var file = 0; file < files.size(); file++) {
                if (placedFiles.get(file)) {
                    continue;
                }
                final var match = matchingTrack(pass, tracks, takenTracks, files.get(file), discs);
                if (match.isPresent()) {
                    trackOfFile[file] = match.getAsInt();
                    placedFiles.set(file);
                    takenTracks.set(match.getAsInt());
                }
            }
        }
        return IntStream.range(0, files.size())
                .mapToObj(file -> placement(placedFiles, trackOfFile, tracks, file))
                .toList();
    }

    private static FilePlacement placement(
            final BitSet placedFiles, final int[] trackOfFile, final List<ReleaseTrack> tracks, final int file) {
        if (placedFiles.get(file)) {
            return new FilePlacement.Placed(tracks.get(trackOfFile[file]));
        }
        return new FilePlacement.Unplaced();
    }

    private static OptionalInt matchingTrack(
            final Pass pass,
            final List<ReleaseTrack> tracks,
            final BitSet takenTracks,
            final FileEvidence file,
            final Discs discs) {
        return IntStream.range(0, tracks.size())
                .filter(track -> !takenTracks.get(track))
                .filter(track -> matches(pass, tracks.get(track), file, discs))
                .findFirst();
    }

    private static boolean matches(
            final Pass pass, final ReleaseTrack track, final FileEvidence file, final Discs discs) {
        return switch (pass) {
            case RECORDING -> hasRecording(track, file);
            case POSITION ->
                hasPosition(track, file, discs) && (track.hasLength(file.duration()) || hasTitle(track, file));
            case TITLE -> hasTitle(track, file) && track.hasLength(file.duration());
        };
    }

    private static boolean hasRecording(final ReleaseTrack track, final FileEvidence file) {
        final var recording = track.track().recording();
        return recording != null && file.recordingIds().contains(recording.id());
    }

    // Without a disc tag, a file can only be placed by its track number on a single disc.
    private static boolean hasPosition(final ReleaseTrack track, final FileEvidence file, final Discs discs) {
        if (file.trackNumber() == null
                || !file.trackNumber().equals(track.track().position())) {
            return false;
        }
        if (file.discNumber() == null) {
            return discs == Discs.ONE;
        }
        return file.discNumber() == track.discNumber();
    }

    private static boolean hasTitle(final ReleaseTrack track, final FileEvidence file) {
        return file.title() != null
                && file.title().equalsIgnoreCase(track.track().title());
    }

    private static Discs discs(final List<ReleaseTrack> tracks) {
        if (tracks.stream().map(ReleaseTrack::discNumber).distinct().count() <= 1) {
            return Discs.ONE;
        }
        return Discs.SEVERAL;
    }

    private enum Discs {
        ONE,
        SEVERAL
    }

    private enum Pass {
        RECORDING,
        POSITION,
        TITLE
    }

    /**
     * A track of a release, with the position of the medium it's on, from 1.
     */
    public record ReleaseTrack(int discNumber, Track track) {

        /**
         * @return whether the track is known to be within the tolerance of the duration
         */
        public boolean hasLength(final Duration duration) {
            if (track.length() == null) {
                return false;
            }
            final var difference =
                    Duration.ofMillis(track.length()).minus(duration).abs();
            return difference.compareTo(LENGTH_TOLERANCE) <= 0;
        }
    }

    /**
     * What a file says about the track it is.
     *
     * @param recordingIds recordings its audio or tags were found to be
     * @param discNumber   its disc number tag; null when it has none
     * @param trackNumber  its track number tag; null when it has none
     * @param title        its title tag; null when it has none
     * @param duration     of its decoded audio
     */
    public record FileEvidence(
            Set<UUID> recordingIds, Integer discNumber, Integer trackNumber, String title, Duration duration) {}

    public sealed interface FilePlacement {

        record Placed(ReleaseTrack track) implements FilePlacement {}

        record Unplaced() implements FilePlacement {}
    }
}
