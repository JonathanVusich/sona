package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.sona.exception.InvalidFormatException;

class SpectrumTest {

    // At 44.1 kHz, short band 0 is 4 lines wide in each window: coded as each window's 4 lines in turn, and taken by
    // the IMDCT as each line's 3 windows in turn.
    @Test
    void reordersShortBands() throws IOException, InvalidFormatException {
        final var spectrum = new Spectrum();
        for (var line = 0; line < Spectrum.LINES; line++) {
            spectrum.setLine(line, line);
        }

        // MPEG-1 at 44.1 kHz.
        spectrum.reorderShortBands(Mp3Fixtures.header("FFFB9000"), Mp3Fixtures.shortBlock());

        final var firstBand = new double[12];
        for (var line = 0; line < firstBand.length; line++) {
            firstBand[line] = spectrum.line(line);
        }
        assertThat(firstBand).containsExactly(0, 4, 8, 1, 5, 9, 2, 6, 10, 3, 7, 11);
    }

    // The first butterfly mixes the last line of subband 0 with the first of subband 1.
    @Test
    void reducesAliasingAcrossSubbands() throws IOException {
        final var spectrum = new Spectrum();
        spectrum.setLine(17, 1);
        spectrum.setLine(18, 2);

        spectrum.reduceAliasing(Mp3Fixtures.longBlock());

        final var cs = AliasCoefficients.cs(0);
        final var ca = AliasCoefficients.ca(0);
        assertThat(spectrum.line(17)).isCloseTo(cs - 2 * ca, within(1e-12));
        assertThat(spectrum.line(18)).isCloseTo(2 * cs + ca, within(1e-12));
    }

    @Test
    void leavesShortBlocksAliased() throws IOException, InvalidFormatException {
        final var spectrum = new Spectrum();
        spectrum.setLine(17, 1);

        spectrum.reduceAliasing(Mp3Fixtures.shortBlock());

        assertThat(spectrum.line(17)).isEqualTo(1);
    }
}
