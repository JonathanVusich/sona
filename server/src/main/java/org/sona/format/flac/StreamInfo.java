package org.sona.format.flac;

import java.time.Duration;
import org.sona.format.MD5Checksum;

public record StreamInfo(
        BlockHeader header,
        MD5Checksum checksum,

        int minBlockSize,
        int maxBlockSize,
        int minFrameSize,
        int maxFrameSize,
        int sampleRate,
        int numberOfChannels,
        int bitsPerSample,
        long interChannelSamples)
        implements Block {

    /**
     * @return how long the audio plays
     */
    public Duration duration() {
        final var nanos = (interChannelSamples % sampleRate) * 1_000_000_000L / sampleRate;
        return Duration.ofSeconds(interChannelSamples / sampleRate, nanos);
    }
}
