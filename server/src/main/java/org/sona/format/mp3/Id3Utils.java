package org.sona.format.mp3;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

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
     */
    static byte[] resynchronise(final byte[] bytes) {
        final var resynchronised = new ByteArrayOutputStream(bytes.length);
        for (var i = 0; i < bytes.length; i++) {
            resynchronised.write(bytes[i]);
            if (bytes[i] == (byte) 0xFF && i + 1 < bytes.length && bytes[i + 1] == 0) {
                i++;
            }
        }
        return resynchronised.toByteArray();
    }

    /**
     * @return the strings from {@code start} to the end of the bytes, which NUL terminators separate, and may end
     */
    static List<String> decodeStrings(final byte[] bytes, final int start, final TextEncoding encoding) {
        final var width = encoding.getTerminatorSize();
        final var strings = new ArrayList<String>();
        var stringStart = start;
        for (var position = start; position + width <= bytes.length; position += width) {
            if (isTerminator(bytes, position, width)) {
                strings.add(new String(bytes, stringStart, position - stringStart, encoding.getCharset()));
                stringStart = position + width;
            }
        }
        if (stringStart < bytes.length) {
            strings.add(new String(bytes, stringStart, bytes.length - stringStart, encoding.getCharset()));
        }
        return strings;
    }

    // UTF-16's terminator is two NULs on a character boundary, as either byte of a character may be 0.
    private static boolean isTerminator(final byte[] bytes, final int position, final int width) {
        for (var i = position; i < position + width; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
