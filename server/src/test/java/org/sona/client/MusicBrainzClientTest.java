package org.sona.client;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.config.JacksonConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Import(JacksonConfig.class)
class MusicBrainzClientTest {

    @Autowired
    MusicBrainzClient client;

    @Test
    void retrieveWeWillRockYou() throws IOException, InterruptedException {
        final var response = client.findTrack("we will rock you");
        assertThat(response).isNotNull();
        assertThat(response.recordings()).isNotEmpty();

        final var firstRecording = response.recordings().getFirst();


    }

}