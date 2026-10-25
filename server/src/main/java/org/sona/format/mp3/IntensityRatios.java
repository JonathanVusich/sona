package org.sona.format.mp3;

import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * How intensity stereo splits a band of the left channel's values between the two channels, by the band's intensity
 * position. MPEG-1 splits it by angle: tan(position * pi / 12) of it to the left for each part to the right. MPEG-2
 * scales one channel down in steps the right channel's scalefac_compress picks: 2^(-1/4) or 2^(-1/2).
 */
@UtilityClass
final class IntensityRatios {

    // MPEG-1's positions run to 6, as 7 marks a band that isn't intensity coded.
    private static final int MPEG_1_POSITIONS = 7;
    // MPEG-2's intensity scale factors take up to 5 bits, and the largest marks a band that isn't intensity coded.
    private static final int LSF_POSITIONS = 31;
    private static final double[] MPEG_1_LEFT = IntStream.range(0, MPEG_1_POSITIONS)
            .mapToDouble(IntensityRatios::mpeg1LeftRatio)
            .toArray();
    // By the lowest bit of scalefac_compress, then position. An odd position scales the left channel.
    private static final double[][] LSF_LEFT = {lsfRatios(0, 1), lsfRatios(1, 1)};
    // An even position scales the right channel, and 0 neither.
    private static final double[][] LSF_RIGHT = {lsfRatios(0, 0), lsfRatios(1, 0)};

    /**
     * @param rightScalefacCompress the right channel's scalefac_compress, whose lowest bit picks MPEG-2's step
     */
    static double leftRatio(final MpegVersion version, final int rightScalefacCompress, final int position) {
        return switch (version) {
            case MPEG_1 -> MPEG_1_LEFT[position];
            case MPEG_2, MPEG_2_5 -> LSF_LEFT[rightScalefacCompress & 1][position];
        };
    }

    /**
     * @param rightScalefacCompress the right channel's scalefac_compress, whose lowest bit picks MPEG-2's step
     */
    static double rightRatio(final MpegVersion version, final int rightScalefacCompress, final int position) {
        return switch (version) {
            case MPEG_1 -> 1 - MPEG_1_LEFT[position];
            case MPEG_2, MPEG_2_5 -> LSF_RIGHT[rightScalefacCompress & 1][position];
        };
    }

    // tan / (1 + tan), as sin / (sin + cos), which holds at position 6, where tan has no value.
    private static double mpeg1LeftRatio(final int position) {
        final var angle = position * Math.PI / 12;
        return Math.sin(angle) / (Math.sin(angle) + Math.cos(angle));
    }

    // A position whose lowest bit is the scaled channel's takes (position + 1) / 2 steps, and others leave it as it is.
    private static double[] lsfRatios(final int compressBit, final int scaledBit) {
        final var step = Math.pow(2, -(1 + compressBit) / 4.0);
        return IntStream.range(0, LSF_POSITIONS)
                .mapToDouble(position -> position % 2 == scaledBit ? Math.pow(step, (position + 1) / 2) : 1)
                .toArray();
    }
}
