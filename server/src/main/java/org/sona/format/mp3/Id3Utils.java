package org.sona.format.mp3;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Helpers for ID3v2 tags. Buffers are read from index 0 to their limit, and must be backed by an array.
 */
@UtilityClass
final class Id3Utils {

    /**
     * @return the value of a synchsafe integer, which keeps the top bit of each byte 0 so it can't be read as a frame
     *     sync (ID3v2.4.0 structure section 6.2)
     */
    static int synchsafeInt(final int bits) {
        return (bits & 0x7F) | ((bits >> 1) & 0x3F80) | ((bits >> 2) & 0x1FC000) | ((bits >> 3) & 0xFE00000);
    }

    /**
     * Undoes unsynchronisation, which puts a 0 byte after every 0xFF byte so no tag data looks like a frame sync
     * (ID3v2.4.0 structure section 6.1).
     *
     * @return the bytes without those zeros, in a buffer of their own
     */
    static ByteBuffer resynchronise(final ByteBuffer bytes) {
        final var length = bytes.limit();
        final var resynchronised = new byte[length];
        var resynchronisedLength = 0;
        for (var i = 0; i < length; i++) {
            final var b = bytes.get(i);
            resynchronised[resynchronisedLength++] = b;
            if (b == (byte) 0xFF && i + 1 < length && bytes.get(i + 1) == 0) {
                i++;
            }
        }
        return ByteBuffer.wrap(resynchronised, 0, resynchronisedLength);
    }

    /**
     * @return the strings from {@code start} to the end of the bytes, which NUL terminators separate, and may end
     */
    static List<String> decodeStrings(final ByteBuffer bytes, final int start, final TextEncoding encoding) {
        final var width = encoding.getTerminatorSize();
        final var length = bytes.limit();
        final var strings = new ArrayList<String>();
        var stringStart = start;
        for (var position = start; position + width <= length; position += width) {
            if (isTerminator(bytes, position, width)) {
                strings.add(decodeString(bytes, stringStart, position, encoding));
                stringStart = position + width;
            }
        }
        if (stringStart < length) {
            strings.add(decodeString(bytes, stringStart, length, encoding));
        }
        return strings;
    }

    // Decodes straight from the buffer's array, without copying the bytes out first.
    private static String decodeString(
            final ByteBuffer bytes, final int start, final int end, final TextEncoding encoding) {
        return new String(bytes.array(), bytes.arrayOffset() + start, end - start, encoding.getCharset());
    }

    // UTF-16's terminator is two NULs on a character boundary, as either byte of a character may be 0.
    private static boolean isTerminator(final ByteBuffer bytes, final int position, final int width) {
        for (var i = position; i < position + width; i++) {
            if (bytes.get(i) != 0) {
                return false;
            }
        }
        return true;
    }
}
