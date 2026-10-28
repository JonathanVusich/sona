package org.sona.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.exception.InvalidFormatException;
import org.sona.format.flac.FlacParser;
import org.sona.format.mp3.Mp3Parser;
import org.sona.samples.FlacAudioSample;
import org.sona.samples.Mp3Sample;

class MeasuredAudioTest {

    @ParameterizedTest
    @EnumSource(FlacAudioSample.class)
    void measureTheDurationOfAllTheAudio(FlacAudioSample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var parsed = new FlacParser().parse(channel);
            final var audio = new MeasuredIntAudio(parsed.audio());

            audio.readToEnd();

            assertThat(audio.duration()).isEqualTo(parsed.audio().streamInfo().duration());
        }
    }

    @ParameterizedTest
    @EnumSource(FlacAudioSample.class)
    void countTheFramesAReaderTookBeforeTheRest(FlacAudioSample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var parsed = new FlacParser().parse(channel);
            final var audio = new MeasuredIntAudio(parsed.audio());
            final var firstFrame = audio.iterator().next();

            assertThat(audio.duration()).isEqualTo(AudioUtils.duration(firstFrame.sampleCount(), audio.sampleRate()));

            // A reader that stopped there leaves the rest to be counted, once.
            audio.readToEnd();
            assertThat(audio.duration()).isEqualTo(parsed.audio().streamInfo().duration());
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = Mp3Sample.class,
            names = {"STEREO_CBR_ID3V24", "HE_32KHZ"})
    void measureTheDurationOfAnMp3sFloatAudio(Mp3Sample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var parsed = new Mp3Parser().parse(channel);
            final var audio = new MeasuredFloatAudio(parsed.audio());

            audio.readToEnd();

            assertThat(audio.duration()).isEqualTo(parsed.audio().streamInfo().duration());
        }
    }
}
