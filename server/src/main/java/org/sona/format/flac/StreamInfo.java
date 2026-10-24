package org.sona.format.flac;

import java.time.Duration;
import org.sona.format.AudioUtils;
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
        return AudioUtils.duration(interChannelSamples, sampleRate);
    }
}
