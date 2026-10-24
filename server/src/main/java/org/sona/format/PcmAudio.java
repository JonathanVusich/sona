package org.sona.format;

/**
 * Decoded audio, read a {@link PcmFrame} at a time as it's iterated.
 */
public interface PcmAudio extends Iterable<PcmFrame> {

    /**
     * @return samples per second, per channel
     */
    int sampleRate();

    /**
     * @return how many bits each sample has, so how large its values may be
     */
    int bitsPerSample();
}
