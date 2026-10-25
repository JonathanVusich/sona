package org.sona.format.mp3;

import java.util.Arrays;
import lombok.experimental.UtilityClass;

/**
 * The butterflies alias reduction applies between neighbouring subbands of long blocks: cs and ca, worked out from the
 * coefficients ci in ISO/IEC 11172-3 Annex B, Table B.9 as {@code cs = 1 / sqrt(1 + ci^2)} and
 * {@code ca = ci / sqrt(1 + ci^2)}.
 */
@UtilityClass
final class AliasCoefficients {

    /**
     * How many butterflies each pair of subbands takes.
     */
    static final int COUNT = 8;

    private static final double[] CI = {-0.6, -0.535, -0.33, -0.185, -0.095, -0.041, -0.0142, -0.0037};
    private static final double[] CS =
            Arrays.stream(CI).map(ci -> 1 / Math.sqrt(1 + ci * ci)).toArray();
    private static final double[] CA =
            Arrays.stream(CI).map(ci -> ci / Math.sqrt(1 + ci * ci)).toArray();

    /**
     * @param butterfly from 0 to {@link #COUNT} - 1
     */
    static double cs(final int butterfly) {
        return CS[butterfly];
    }

    /**
     * @param butterfly from 0 to {@link #COUNT} - 1
     */
    static double ca(final int butterfly) {
        return CA[butterfly];
    }
}
