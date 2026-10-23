package org.sona.fingerprint;

import java.io.ByteArrayOutputStream;

/**
 * Packs values of a few bits each into bytes, least significant bit first, as Chromaprint's compressed fingerprints
 * store them.
 */
public final class BitPacker {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private int pending;
    private int pendingBits;

    /**
     * @param bits from 1 to 24
     */
    public void writeBits(final int value, final int bits) {
        pending |= (value & ((1 << bits) - 1)) << pendingBits;
        pendingBits += bits;
        while (pendingBits >= Byte.SIZE) {
            bytes.write(pending);
            pending >>>= Byte.SIZE;
            pendingBits -= Byte.SIZE;
        }
    }

    /**
     * Fills the rest of the current byte with zeros, so the next value starts a byte of its own.
     */
    public void alignToByte() {
        if (pendingBits > 0) {
            writeBits(0, Byte.SIZE - pendingBits);
        }
    }

    /**
     * @return the bytes written so far, the last one filled with zeros
     */
    public byte[] toByteArray() {
        alignToByte();
        return bytes.toByteArray();
    }
}
