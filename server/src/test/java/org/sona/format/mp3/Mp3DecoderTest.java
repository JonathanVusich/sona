package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.exception.InvalidFormatException;
import org.sona.samples.Mp3Sample;

class Mp3DecoderTest {

    // Each frame's side information gives how long it is and where its main data starts, and each granule's channel
    // starts with its scale factors, then its values, so every frame and granule is only read whole when the side
    // information and scale factors are read right.
    @ParameterizedTest
    @EnumSource(
            value = Mp3Sample.class,
            names = {"FREE_FORMAT"},
            mode = EnumSource.Mode.EXCLUDE)
    void readsEveryFrame(final Mp3Sample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var decoder = new Mp3Parser().parse(channel).audio();
            final var spectrum = new Spectrum();

            for (var frame = 0; frame < decoder.streamInfo().frameCount(); frame++) {
                final var mp3Frame = decoder.readFrame();

                assertThat(granuleBits(mp3Frame)).isLessThanOrEqualTo(mp3Frame.mainDataLength() * Byte.SIZE);
                readGranules(decoder, spectrum, mp3Frame);
            }
        }
    }

    private static int granuleBits(final Mp3Frame frame) {
        return frame.sideInfo().granules().stream()
                .flatMap(List::stream)
                .mapToInt(GranuleChannel::part23Length)
                .sum();
    }

    // Reading a channel's values fails when its scale factors take more bits than its part2_3_length.
    private static void readGranules(final Mp3Decoder decoder, final Spectrum spectrum, final Mp3Frame frame)
            throws IOException, InvalidFormatException {
        final var header = frame.header();
        for (var granule = 0; granule < header.granuleCount(); granule++) {
            for (var channel = 0; channel < header.channelCount(); channel++) {
                final var granuleChannel =
                        frame.sideInfo().granules().get(granule).get(channel);
                final var scaleFactors = decoder.readScaleFactors(frame, granule, channel);
                spectrum.read(frame.mainData(), header, granuleChannel, scaleFactors);
            }
        }
    }
}
