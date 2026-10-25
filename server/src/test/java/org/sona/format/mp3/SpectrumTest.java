package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.exception.InvalidFormatException;
import org.sona.samples.Mp3Sample;

class SpectrumTest {

    private static final double TONE_HZ = 440;

    // Every sample is a 440 Hz tone. In a long block, line k of subband 0 is centred on (k + 1/2) * sampleRate / 1152
    // Hz, so the loudest line of each granule that holds the tone is the one nearest 440 Hz.
    @ParameterizedTest
    @EnumSource(
            value = Mp3Sample.class,
            names = {"FREE_FORMAT"},
            mode = EnumSource.Mode.EXCLUDE)
    void loudestLineIsTheTone(final Mp3Sample sample) throws IOException, InvalidFormatException {
        final var granules = decodeFirstChannel(sample);

        assertThat(granules).isNotEmpty().allSatisfy(granule -> {
            final var toneLine = TONE_HZ * 2 * Spectrum.LINES / granule.sampleRate() - 0.5;
            assertThat((double) granule.loudestLine()).isBetween(toneLine - 1, toneLine + 1);
        });
    }

    // The long-block granules of the first channel that hold the tone, which the first granules may not, as the
    // encoder's delay is silence.
    private static List<DecodedGranule> decodeFirstChannel(final Mp3Sample sample)
            throws IOException, InvalidFormatException {
        try (final var channel = sample.channel()) {
            final var decoder = new Mp3Parser().parse(channel).audio();
            final var granules = new ArrayList<DecodedGranule>();
            for (var frame = 0; frame < decoder.streamInfo().frameCount(); frame++) {
                granules.addAll(decodeFrame(decoder, decoder.readFrame()));
            }
            final var loudest =
                    granules.stream().mapToDouble(DecodedGranule::peak).max().orElse(0);
            return granules.stream()
                    .filter(granule -> granule.peak() > loudest / 4 && granule.blockType() == BlockType.NORMAL)
                    .toList();
        }
    }

    private static List<DecodedGranule> decodeFrame(final Mp3Decoder decoder, final Mp3Frame frame)
            throws IOException, InvalidFormatException {
        final var decoded = new ArrayList<DecodedGranule>();
        final var spectrum = new Spectrum();
        final var header = frame.header();
        for (var granule = 0; granule < header.granuleCount(); granule++) {
            for (var channel = 0; channel < header.channelCount(); channel++) {
                final var granuleChannel =
                        frame.sideInfo().granules().get(granule).get(channel);
                final var channelFactors = decoder.readScaleFactors(frame, granule, channel);
                spectrum.read(frame.mainData(), header, granuleChannel, channelFactors);
                if (channel == 0) {
                    decoded.add(new DecodedGranule(
                            header.sampleRate(),
                            granuleChannel.windowing().blockType(),
                            loudestLine(spectrum),
                            Math.abs(spectrum.line(loudestLine(spectrum)))));
                }
            }
        }
        return decoded;
    }

    private static int loudestLine(final Spectrum spectrum) {
        var loudest = 0;
        for (var line = 1; line < Spectrum.LINES; line++) {
            if (Math.abs(spectrum.line(line)) > Math.abs(spectrum.line(loudest))) {
                loudest = line;
            }
        }
        return loudest;
    }

    private record DecodedGranule(int sampleRate, BlockType blockType, int loudestLine, double peak) {}
}
