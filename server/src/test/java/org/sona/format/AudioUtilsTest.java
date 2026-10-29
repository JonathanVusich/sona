package org.sona.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.exception.InvalidFormatException;
import org.sona.format.flac.FlacParser;
import org.sona.format.mp3.Mp3Parser;
import org.sona.samples.FlacAudioSample;
import org.sona.samples.Mp3Sample;

class AudioUtilsTest {

    @Test
    void durationOfWholeSeconds() {
        assertThat(AudioUtils.duration(88200, 44100)).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void durationKeepsFractionOfASecond() {
        assertThat(AudioUtils.duration(46080, 44100)).isEqualTo(Duration.ofSeconds(1, 44_897_959));
    }

    @ParameterizedTest
    @EnumSource(FlacAudioSample.class)
    void measureTheDurationOfAllTheIntegerAudio(FlacAudioSample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var parsed = new FlacParser().parse(channel);

            assertThat(AudioUtils.duration(parsed.audio()))
                    .isEqualTo(parsed.audio().streamInfo().duration());
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = Mp3Sample.class,
            names = {"STEREO_CBR_ID3V24", "HE_32KHZ"})
    void measureTheDurationOfAllTheFloatAudio(Mp3Sample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var parsed = new Mp3Parser().parse(channel);

            assertThat(AudioUtils.duration(parsed.audio()))
                    .isEqualTo(parsed.audio().streamInfo().duration());
        }
    }
}
