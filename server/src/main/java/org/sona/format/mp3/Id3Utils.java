package org.sona.format.mp3;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Helpers for ID3v2 tags, which read the range of an array from {@code start} up to {@code end}.
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
     * @return the big-endian integer in the four bytes at the offset
     */
    static int bigEndianInt(final byte[] bytes, final int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    /**
     * Undoes unsynchronisation, which puts a 0 byte after every 0xFF byte so no tag data looks like a frame sync
     * (ID3v2.4.0 structure section 6.1).
     *
     * @return the range without those zeros, in an array of its own
     */
    static byte[] resynchronise(final byte[] bytes, final int start, final int end) {
        final var resynchronised = new byte[end - start - insertedZeros(bytes, start, end)];
        var length = 0;
        for (var i = start; i < end; i++) {
            resynchronised[length++] = bytes[i];
            if (isInsertedZero(bytes, i + 1, end)) {
                i++;
            }
        }
        return resynchronised;
    }

    /**
     * @return the strings in the range, which NUL terminators separate, and may end
     */
    static List<String> decodeStrings(final byte[] bytes, final int start, final int end, final TextEncoding encoding) {
        final var width = encoding.getTerminatorSize();
        final var strings = new ArrayList<String>();
        var stringStart = start;
        for (var position = start; position + width <= end; position += width) {
            if (isTerminator(bytes, position, width)) {
                strings.add(new String(bytes, stringStart, position - stringStart, encoding.getCharset()));
                stringStart = position + width;
            }
        }
        if (stringStart < end) {
            strings.add(new String(bytes, stringStart, end - stringStart, encoding.getCharset()));
        }
        return strings;
    }

    // Counted first, so the resynchronised array can be made its exact size.
    private static int insertedZeros(final byte[] bytes, final int start, final int end) {
        var count = 0;
        for (var i = start; i < end; i++) {
            if (isInsertedZero(bytes, i + 1, end)) {
                count++;
                i++;
            }
        }
        return count;
    }

    // A zero that unsynchronisation put after an 0xFF byte.
    private static boolean isInsertedZero(final byte[] bytes, final int index, final int end) {
        return index < end && bytes[index - 1] == (byte) 0xFF && bytes[index] == 0;
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
