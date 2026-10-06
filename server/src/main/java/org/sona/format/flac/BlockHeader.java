package org.sona.format.flac;

public record BlockHeader(
        boolean lastBlock,
        BlockType blockType,
        int size
) {
}
