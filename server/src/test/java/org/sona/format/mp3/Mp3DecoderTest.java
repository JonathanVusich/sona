package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.exception.InvalidFormatException;
import org.sona.samples.Mp3Sample;

class Mp3DecoderTest {

    // Each frame's side information gives how long it is and where its main data starts, so every frame is only read
    // whole when the side information is read right.
    @ParameterizedTest
    @EnumSource(
            value = Mp3Sample.class,
            names = {"FREE_FORMAT"},
            mode = EnumSource.Mode.EXCLUDE)
    void readsEveryFrame(final Mp3Sample sample) throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var decoder = new Mp3Parser().parse(channel).audio();

            for (var frame = 0; frame < decoder.streamInfo().frameCount(); frame++) {
                final var mp3Frame = decoder.readFrame();

                assertThat(granuleBits(mp3Frame)).isLessThanOrEqualTo(mp3Frame.mainDataLength() * Byte.SIZE);
            }
        }
    }

    private static int granuleBits(final Mp3Frame frame) {
        return frame.sideInfo().granules().stream()
                .flatMap(List::stream)
                .mapToInt(GranuleChannel::part23Length)
                .sum();
    }
}
