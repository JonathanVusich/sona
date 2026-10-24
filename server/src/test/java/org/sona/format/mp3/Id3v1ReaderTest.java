package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.channels.Channels;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sona.format.mp3.Id3Frame.TextFrame;
import org.sona.samples.Mp3Sample;

class Id3v1ReaderTest {

    // Each field is returned as the ID3v2.3 frame that holds the same data.
    @Test
    void readsFieldsAsId3v2Frames() throws IOException {
        assertThat(readTag(Mp3Sample.NO_XING_ID3V1))
                .containsExactly(
                        new TextFrame("TIT2", List.of("Untagged")),
                        new TextFrame("TPE1", List.of("Nobody")),
                        new TextFrame("TALB", List.of("Nothing")),
                        new TextFrame("TRCK", List.of("7")));
    }

    @Test
    void readsNoFieldsWhenFileEndsWithAudio() throws IOException {
        assertThat(readTag(Mp3Sample.STEREO_CBR_ID3V24)).isEmpty();
    }

    private static List<TextFrame> readTag(final Mp3Sample sample) throws IOException {
        try (final var channel = sample.channel()) {
            final var tail = channel.position(channel.size() - Id3v1Reader.TAG_SIZE);
            return new Id3v1Reader().readTag(Channels.newInputStream(tail));
        }
    }
}
