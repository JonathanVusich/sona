package org.sona.format.mp3;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sona.exception.InvalidFormatException;

/**
 * How an ID3v2 frame encodes its text, given by the frame's first byte (ID3v2.4.0 structure section 4).
 */
@Getter
@RequiredArgsConstructor
enum TextEncoding {
    ISO_8859_1(StandardCharsets.ISO_8859_1, 1),
    // Each string starts with its own byte order mark.
    UTF_16(StandardCharsets.UTF_16, 2),
    // ID3v2.4 only, like UTF_8.
    UTF_16BE(StandardCharsets.UTF_16BE, 2),
    UTF_8(StandardCharsets.UTF_8, 1);

    private final Charset charset;
    // How many NUL bytes end a string.
    private final int terminatorSize;

    static TextEncoding fromByte(final int encodingByte) throws InvalidFormatException {
        return switch (encodingByte) {
            case 0 -> ISO_8859_1;
            case 1 -> UTF_16;
            case 2 -> UTF_16BE;
            case 3 -> UTF_8;
            default -> throw new InvalidFormatException("Unknown ID3 text encoding: " + encodingByte);
        };
    }
}
