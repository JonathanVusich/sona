package org.sona.format.mp3;

/**
 * A channel's samples of the 32 subbands, at each instant, which the polyphase synthesis filterbank turns into PCM:
 * those Layer III's IMDCT gives, or those Layers I and II code directly.
 */
interface SubbandSamples {

    /**
     * @param instant from 0 to the number of instants the samples hold, less 1
     * @param subband from 0 to 31
     */
    double subbandSample(int instant, int subband);
}
