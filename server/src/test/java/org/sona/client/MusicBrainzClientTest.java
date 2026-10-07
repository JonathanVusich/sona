package org.sona.client;

import org.junit.jupiter.api.Test;
import org.sona.client.model.query.RecordingQuery;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MusicBrainzClientTest {

    private final MusicBrainzFixtureServer server = MusicBrainzFixtureServer.instance();
    private final MusicBrainzClient client = server.client();

    @Test
    void searchRecordings() {
        final var response = client.searchRecordings(new RecordingQuery("A Whisper", "Coldplay", 9, -1));

        assertThat(response.count()).isEqualTo(3);
        final var recording = response.recordings().getFirst();
        assertThat(recording.id()).isEqualTo(UUID.fromString("53616964-f51e-4ce0-a589-a5581f771197"));
        assertThat(recording.title()).isEqualTo("A Whisper");
        assertThat(recording.score()).isEqualTo(100);

        final var credit = recording.artistCredit().getFirst();
        assertThat(credit.name()).isEqualTo("Coldplay");

        final var release = recording.releases().getFirst();
        assertThat(release.title()).isEqualTo("A Rush of Blood to the Head");
        assertThat(release.releaseGroup().id()).isEqualTo(UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf"));
    }

    @Test
    void sendQueryWithReservedCharactersIntact() {
        final var query = new RecordingQuery("Rock & Roll + {Live} 100%", "Simon & Garfunkel", -1, -1);

        // No fixture exists, so the request fails, but the server must have received the exact query.
        assertThatThrownBy(() -> client.searchRecordings(query)).isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThat(server.lastQuery()).isEqualTo(query.build());
    }

    @Test
    void failOnErrorStatus() {
        assertThatThrownBy(() -> client.searchRecordings(new RecordingQuery("HTTP 503", null, -1, -1)))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
    }
}
