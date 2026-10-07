package org.sona.format.flac;

import java.util.List;
import java.util.Map;

public record FlacMetadata(
        Map<BlockType, List<Block>> metadataBlocks
) {
}
