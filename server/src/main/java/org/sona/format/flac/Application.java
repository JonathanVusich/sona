package org.sona.format.flac;

public record Application(
        BlockHeader header,
        int applicationId
) implements Block {
}
