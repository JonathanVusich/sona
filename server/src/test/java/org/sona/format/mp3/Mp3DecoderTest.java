package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.javax.bitstream.BitInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.exception.InvalidFormatException;
import org.sona.exception.UncheckedInvalidFormatException;
import org.sona.format.PcmFrame;
import org.sona.samples.Mp3Sample;

class Mp3DecoderTest {

    // ISO/IEC 11172-4 counts a decoder compliant when its 16-bit samples are within 1 of the reference decoder's.
    private static final int TOLERANCE = 1;

    // Each frame's side information gives how long it is and where its main data starts, and each granule's channel
    // starts with its scale factors, then its values, so every frame is only decoded whole, with no granule running
    // past its main data, when the side information and scale factors are read right.
    @ParameterizedTest
    @EnumSource(
            value = Mp3Sample.class,
            names = {"FREE_FORMAT"},
            mode = EnumSource.Mode.EXCLUDE)
    void decodesEveryFrame(final Mp3Sample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var audio = new Mp3Parser().parse(channel).audio();
            final var streamInfo = audio.streamInfo();

            assertThat(interleave(audio)).hasSize((int) streamInfo.sampleCount() * streamInfo.channelCount());
        }
    }

    // FFmpeg trims the same samples from the start. It doesn't trim the padding an MPEG-2 file's LAME tag gives from
    // its end, so it gives more samples there.
    @ParameterizedTest
    @EnumSource(
            value = Mp3Sample.class,
            names = {"STEREO_CBR_ID3V24", "MONO_VBR_ID3V23", "NO_XING_ID3V1"})
    void decodesAsFfmpegDoes(final Mp3Sample sample) throws IOException, InvalidFormatException {
        final var reference = ffmpegSamples(sample);
        try (final var channel = sample.channel()) {
            final var audio = new Mp3Parser().parse(channel).audio();
            final var decoded = interleave(audio);
            final var streamInfo = audio.streamInfo();

            assertThat(decoded.length).isEqualTo(streamInfo.sampleCount() * streamInfo.channelCount());
            assertThat(reference.length).isGreaterThanOrEqualTo(decoded.length);
            // The tone is loud, so the samples can't match by both being near silence.
            assertThat(Arrays.stream(decoded).max().orElseThrow()).isGreaterThan(1000);
            for (var index = 0; index < decoded.length; index++) {
                assertThat(decoded[index]).as("sample %d", index).isCloseTo(reference[index], within(TOLERANCE));
            }
        }
    }

    @Test
    void refusesFreeFormatFrames() throws IOException, InvalidFormatException {
        try (final var channel = Mp3Sample.FREE_FORMAT.channel()) {
            final var audio = new Mp3Parser().parse(channel).audio();

            assertThatThrownBy(() -> audio.iterator().next())
                    .isInstanceOf(UncheckedInvalidFormatException.class)
                    .hasMessageContaining("Free-format");
        }
    }

    private static int[] interleave(final Mp3Decoder audio) {
        final var channels = audio.streamInfo().channelCount();
        final var samples = new int[(int) audio.streamInfo().sampleCount() * channels];
        var index = 0;
        for (final PcmFrame frame : audio) {
            for (var sample = 0; sample < frame.sampleCount(); sample++) {
                for (var channel = 0; channel < channels; channel++) {
                    samples[index++] = frame.channelSample(channel, sample);
                }
            }
        }
        return Arrays.copyOf(samples, index);
    }

    // Each sample is 16 bits, little-endian and signed.
    private static int[] ffmpegSamples(final Mp3Sample sample) throws IOException {
        try (final var input = sample.ffmpegPcm()) {
            final var bytes = input.readAllBytes();
            final var bits = BitInputStream.wrap(new ByteArrayInputStream(bytes), ByteOrder.LITTLE_ENDIAN);
            final var samples = new int[bytes.length / Short.BYTES];
            for (var index = 0; index < samples.length; index++) {
                samples[index] = (short) bits.readBits(Short.SIZE);
            }
            return samples;
        }
    }
}
