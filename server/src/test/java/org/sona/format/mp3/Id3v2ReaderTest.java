package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sona.exception.InvalidFormatException;
import org.sona.format.mp3.Id3Frame.TextFrame;
import org.sona.format.mp3.Id3Frame.UserTextFrame;
import org.sona.samples.Mp3Sample;

class Id3v2ReaderTest {

    @Test
    void readsId3v24Tag() throws IOException, InvalidFormatException {
        final var tag = readTag(Mp3Sample.STEREO_CBR_ID3V24).orElseThrow();

        // The tag's padding runs up to the first frame, at 291.
        assertThat(tag.size()).isEqualTo(291);
        assertThat(tag.frames())
                .contains(
                        new TextFrame("TIT2", List.of("A Whisper")),
                        new TextFrame("TPE1", List.of("Coldplay")),
                        new TextFrame("TRCK", List.of("9/11")),
                        new TextFrame("TDOR", List.of("2002-08-12")),
                        new UserTextFrame("MusicBrainz Album Id", List.of("5b59d66b-8902-4350-b2e3-324037ef4cb0")));
    }

    @Test
    void readsId3v23Tag() throws IOException, InvalidFormatException {
        final var tag = readTag(Mp3Sample.MONO_VBR_ID3V23).orElseThrow();

        assertThat(tag.size()).isEqualTo(133);
        assertThat(tag.frames())
                .contains(
                        new TextFrame("TIT2", List.of("Canyon")),
                        new TextFrame("TPE1", List.of("Lucas Floyd")),
                        new TextFrame("TRCK", List.of("3")),
                        new TextFrame("TORY", List.of("2019")));
    }

    @Test
    void readsNoTagWhenFileStartsWithAudio() throws IOException, InvalidFormatException {
        assertThat(readTag(Mp3Sample.NO_XING_ID3V1)).isEmpty();
    }

    private static Optional<Id3v2Tag> readTag(final Mp3Sample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            return new Id3v2Reader().readTag(channel);
        }
    }
}
