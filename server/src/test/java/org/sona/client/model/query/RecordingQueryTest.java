package org.sona.client.model.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingQueryTest {

    @Test
    void titleOnly() {
        assertThat(new RecordingQuery("we will rock you", null, -1, -1).build())
                .isEqualTo("\"we will rock you\"");
    }

    @Test
    void quoteMultiWordArtist() {
        assertThat(new RecordingQuery("Canyon", "Philip Glass", 8, 14).build())
                .isEqualTo("\"Canyon\" AND artist:\"Philip Glass\" AND tnum:8 AND tracks:14");
    }
}
