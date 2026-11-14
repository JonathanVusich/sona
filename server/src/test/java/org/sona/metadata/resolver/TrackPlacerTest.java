package org.sona.metadata.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.metadata.resolver.TrackPlacer.placeFiles;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sona.client.model.Recording;
import org.sona.client.model.Track;
import org.sona.metadata.resolver.TrackPlacer.FileEvidence;
import org.sona.metadata.resolver.TrackPlacer.FilePlacement;
import org.sona.metadata.resolver.TrackPlacer.ReleaseTrack;

class TrackPlacerTest {

    private static final Duration FOUR_MINUTES = Duration.ofMinutes(4);

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final ReleaseTrack firstTrack = track(1, 1, first, "First", FOUR_MINUTES);
    private final ReleaseTrack secondTrack = track(1, 2, second, "Second", FOUR_MINUTES);

    @Test
    void placeAFileOnTheTrackOfItsRecordingWhateverItsTagsSay() {
        final var file = new FileEvidence(Set.of(second), 1, 1, "First", FOUR_MINUTES);

        assertThat(placeFiles(List.of(firstTrack, secondTrack), List.of(file)))
                .containsExactly(new FilePlacement.Placed(secondTrack));
    }

    @Test
    void placeAFileByItsTrackNumberWhenItsLengthAgrees() {
        final var file = new FileEvidence(Set.of(), null, 2, "Untitled", FOUR_MINUTES.plusSeconds(3));

        assertThat(placeFiles(List.of(firstTrack, secondTrack), List.of(file)))
                .containsExactly(new FilePlacement.Placed(secondTrack));
    }

    @Test
    void placeAFileByItsTrackNumberWhenItsTitleAgrees() {
        final var file = new FileEvidence(Set.of(), null, 2, "second", Duration.ofMinutes(9));

        assertThat(placeFiles(List.of(firstTrack, secondTrack), List.of(file)))
                .containsExactly(new FilePlacement.Placed(secondTrack));
    }

    @Test
    void leaveAFileWhoseTrackNumberIsOfATrackOfAnotherLengthAndTitle() {
        final var file = new FileEvidence(Set.of(), null, 2, "Untitled", Duration.ofMinutes(9));

        assertThat(placeFiles(List.of(firstTrack, secondTrack), List.of(file)))
                .containsExactly(new FilePlacement.Unplaced());
    }

    @Test
    void placeAFileByItsTrackNumberOnSeveralDiscsOnlyWithItsDiscNumber() {
        final var discTwo = track(2, 1, UUID.randomUUID(), "Disc Two", FOUR_MINUTES);
        final var untagged = new FileEvidence(Set.of(), null, 1, "Untitled", FOUR_MINUTES);
        final var tagged = new FileEvidence(Set.of(), 2, 1, "Untitled", FOUR_MINUTES);

        assertThat(placeFiles(List.of(firstTrack, discTwo), List.of(untagged, tagged)))
                .containsExactly(new FilePlacement.Unplaced(), new FilePlacement.Placed(discTwo));
    }

    @Test
    void placeAFileByItsTitleAndLength() {
        final var file = new FileEvidence(Set.of(), null, null, "Second", FOUR_MINUTES);

        assertThat(placeFiles(List.of(firstTrack, secondTrack), List.of(file)))
                .containsExactly(new FilePlacement.Placed(secondTrack));
    }

    @Test
    void placeEachFileOnADifferentTrack() {
        final var byRecording = new FileEvidence(Set.of(first), null, null, null, FOUR_MINUTES);
        final var sameRecording = new FileEvidence(Set.of(first), null, null, null, FOUR_MINUTES);
        final var byTitle = new FileEvidence(Set.of(), null, null, "First", FOUR_MINUTES);

        assertThat(placeFiles(List.of(firstTrack, secondTrack), List.of(byRecording, sameRecording, byTitle)))
                .containsExactly(
                        new FilePlacement.Placed(firstTrack),
                        new FilePlacement.Unplaced(),
                        new FilePlacement.Unplaced());
    }

    @Test
    void aTrackOfUnknownLengthIsNotAsLongAsAnyFile() {
        final var unknownLength = new ReleaseTrack(1, new Track(UUID.randomUUID(), "1", "First", null, 1, null, null));

        assertThat(unknownLength.hasLength(FOUR_MINUTES)).isFalse();
    }

    private static ReleaseTrack track(
            final int disc, final int position, final UUID recordingId, final String title, final Duration length) {
        final var millis = (int) length.toMillis();
        final var recording = new Recording(recordingId, title, millis, null, null, null, null);
        return new ReleaseTrack(
                disc, new Track(UUID.randomUUID(), String.valueOf(position), title, millis, position, recording, null));
    }
}
