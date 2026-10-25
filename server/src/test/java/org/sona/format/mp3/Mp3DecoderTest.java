package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.javax.bitstream.BitInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.exception.InvalidFormatException;
import org.sona.format.PcmFrame;
import org.sona.samples.Mp3Sample;

class Mp3DecoderTest {

    // ISO/IEC 11172-4 counts a decoder compliant when its 16-bit samples are within 1 of the reference decoder's.
    private static final int TOLERANCE = 1;
    // An MPEG-1 frame's samples, in stereo.
    private static final int FRAME_SAMPLES = 2 * 2 * FrameHeader.GRANULE_SIZE;

    // Each frame's side information gives how long it is and where its main data starts, and each granule's channel
    // starts with its scale factors, then its values, so every frame is only decoded whole, with no granule running
    // past its main data, when the side information and scale factors are read right.
    @ParameterizedTest
    @EnumSource(Mp3Sample.class)
    void decodesEveryFrame(final Mp3Sample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var audio = new Mp3Parser().parse(channel).audio();
            var samples = 0L;
            for (final PcmFrame frame : audio) {
                samples += frame.sampleCount();
            }

            assertThat(samples).isEqualTo(audio.streamInfo().sampleCount());
        }
    }

    // FFmpeg trims the same samples from the start of a file with a LAME tag. It doesn't trim the padding an MPEG-2
    // file's LAME tag gives from its end, and the conformance tests' reference decoder doesn't give their last frame,
    // so the two may differ by a frame at the end.
    @ParameterizedTest
    @EnumSource(
            value = Mp3Sample.class,
            names = {
                "STEREO_CBR_ID3V24",
                "MONO_VBR_ID3V23",
                "NO_XING_ID3V1",
                "HE_32KHZ",
                "HE_48KHZ",
                "HE_FREE",
                "HECOMMON",
                "SI",
                "SI_BLOCK",
                "SI_HUFF",
                "HE_MODE",
                "MPEG1_SHORT_BLOCKS",
                "MPEG2_SHORT_BLOCKS",
                "MPEG25_SHORT_BLOCKS",
                "SHINE"
            })
    void decodesAsReferenceDecoderDoes(final Mp3Sample sample) throws IOException, InvalidFormatException {
        final var reference = referenceSamples(sample);
        final var decoded = decode(sample);

        assertThat(decoded.length).isCloseTo(reference.length, within(FRAME_SAMPLES));
        // The audio is loud, so the samples can't match by both being near silence.
        assertThat(Arrays.stream(decoded).max().orElseThrow()).isGreaterThan(500);
        for (var index = 0; index < Math.min(decoded.length, reference.length); index++) {
            assertThat(decoded[index]).as("sample %d", index).isCloseTo(reference[index], within(TOLERANCE));
        }
    }

    // The free-format sample is the fixed-bitrate one with its headers' bitrates cleared, which FFmpeg can't decode.
    @Test
    void decodesFreeFormatAsItsSourceStream() throws IOException, InvalidFormatException {
        assertThat(decode(Mp3Sample.FREE_FORMAT)).containsExactly(decode(Mp3Sample.NO_XING_ID3V1));
    }

    // Each frame's samples, with its own channels interleaved.
    private static int[] decode(final Mp3Sample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var samples = IntStream.builder();
            for (final PcmFrame frame : new Mp3Parser().parse(channel).audio()) {
                for (var index = 0; index < frame.sampleCount(); index++) {
                    for (var frameChannel = 0; frameChannel < frame.channelCount(); frameChannel++) {
                        samples.add(frame.channelSample(frameChannel, index));
                    }
                }
            }
            return samples.build().toArray();
        }
    }

    // Each sample is 16 bits, little-endian and signed.
    private static int[] referenceSamples(final Mp3Sample sample) throws IOException {
        try (final var input = sample.referencePcm()) {
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
