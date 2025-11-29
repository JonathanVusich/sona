package org.sona.format.flac;

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
        long interChannelSamples
) implements Block {
}
