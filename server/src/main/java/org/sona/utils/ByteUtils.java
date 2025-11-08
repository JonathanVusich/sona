package org.sona.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class ByteUtils {

    public static int unsigned(byte val) {
        return val & 0xff;
    }

    public static boolean isBitSet(int unsignedByte, int bitIndex) {
        return ((unsignedByte >> (bitIndex - 1)) & 1) == 0;
    }

    public static short toShort(byte byte1, byte byte2) {
        return (short) ((unsigned(byte1) << 8) | unsigned(byte2));
    }

    public int toU20(byte byte1, byte byte2, byte byte3) {
        // return unsigned(byte1) | unsigned(byte2) | unsigned(byte3);
        throw new IllegalArgumentException();
    }

    public static int toU24(byte byte1, byte byte2, byte byte3) {
        return (unsigned(byte1) << 16) | (unsigned(byte2) << 8) | unsigned(byte3);
    }

    public static int toInt(byte byte1, byte byte2, byte byte3, byte byte4) {
        return unsigned(byte1) | unsigned(byte2) | unsigned(byte3) | unsigned(byte4);
    }

}
