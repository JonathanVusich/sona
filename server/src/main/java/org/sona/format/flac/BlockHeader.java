package org.sona.format.flac;

public record BlockHeader(
        BlockPosition position,
        BlockType blockType,
        int size
) {
}
