package org.sona.format;

/**
 * Decoded audio, read a frame at a time as it's iterated, all of one kind of {@link PcmFrame}.
 */
public interface PcmAudio<F extends PcmFrame> extends Iterable<F> {

    /**
     * @return samples per second, per channel
     */
    int sampleRate();

    /**
     * @return how many bits each sample has, so how large its values may be
     */
    int bitsPerSample();
}
