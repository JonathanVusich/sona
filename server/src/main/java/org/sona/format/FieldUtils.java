package org.sona.format;

import java.nio.charset.Charset;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class FieldUtils {

    /**
     * @param size the field's size, which the text fills or ends early with a NUL, followed by padding
     * @return the text of a fixed-size field, up to its first NUL
     */
    public static String nulPaddedString(final byte[] bytes, final int offset, final int size, final Charset charset) {
        var length = 0;
        while (length < size && bytes[offset + length] != 0) {
            length++;
        }
        return new String(bytes, offset, length, charset);
    }
}
