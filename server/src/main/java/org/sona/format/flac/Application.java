package org.sona.format.flac;

// https://www.rfc-editor.org/rfc/rfc9639.html#name-application
public record Application(
        BlockHeader header,
        int applicationId,
        byte[] data
) implements Block {
}
