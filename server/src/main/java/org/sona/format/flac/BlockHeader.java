package org.sona.format.flac;

import lombok.Getter;
import org.sona.model.exception.InvalidFormatException;
import org.sona.service.metadata.parsing.FlacParser;
import org.sona.utils.ByteUtils;

import java.io.IOException;
import java.io.InputStream;

public record BlockHeader(
        boolean lastBlock,
        BlockType blockType,
        int size
) {
}
