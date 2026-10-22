package org.sona.format.flac;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads the bits of FLAC frames, most significant first, and keeps the CRC-8 of the frame header and the CRC-16 of
 * the whole frame (RFC 9639 sections 9.1.8 and 9.3) over the bytes read since the frame started.
 */
final class FrameReader {

    private static final int[] CRC8_TABLE = crcTable(0x07, 8);
    private static final int[] CRC16_TABLE = crcTable(0x8005, 16);

    private final InputStream inputStream;
    private final byte[] buffer = new byte[64 * 1024];
    private int position;
    private int limit;

    // The low `cachedBits` bits of `cache` are the next bits to read. A byte is only taken from the buffer once one
    // of its bits is needed, so the CRCs cover exactly the bytes read so far.
    private long cache;
    private int cachedBits;

    private int crc8;
    private int crc16;

    FrameReader(final InputStream inputStream) {
        this.inputStream = inputStream;
    }

    /**
     * Starts the CRCs over for a new frame.
     *
     * @return false if the stream has no more bytes
     */
    boolean startFrame() throws IOException {
        crc8 = 0;
        crc16 = 0;
        return cachedBits > 0 || position < limit || fill();
    }

    long readBits(final int count) throws IOException {
        while (cachedBits < count) {
            cache = (cache << 8) | nextByte();
            cachedBits += 8;
        }
        cachedBits -= count;
        return (cache >>> cachedBits) & ((1L << count) - 1);
    }

    long readSigned(final int count) throws IOException {
        if (count == 0) {
            return 0;
        }
        final var shift = 64 - count;
        return (readBits(count) << shift) >> shift;
    }

    /**
     * @return the number of 0 bits before the next 1 bit, which is read too
     */
    int readUnary() throws IOException {
        var zeros = 0;
        while (true) {
            if (cachedBits == 0) {
                cache = nextByte();
                cachedBits = 8;
            }
            final var window = cache & ((1L << cachedBits) - 1);
            if (window == 0) {
                zeros += cachedBits;
                cachedBits = 0;
            } else {
                final var highestOne = 63 - Long.numberOfLeadingZeros(window);
                zeros += cachedBits - 1 - highestOne;
                cachedBits = highestOne;
                return zeros;
            }
        }
    }

    /**
     * Skips to the start of the next byte, as each frame's subframes are padded to whole bytes.
     */
    void alignToByte() {
        cachedBits -= cachedBits % 8;
    }

    /**
     * @return the CRC-8 of the frame so far: 0 once a frame header has been read along with its CRC
     */
    int crc8() {
        return crc8;
    }

    /**
     * @return the CRC-16 of the frame so far: 0 once a whole frame has been read along with its CRC
     */
    int crc16() {
        return crc16;
    }

    private int nextByte() throws IOException {
        if (position == limit && !fill()) {
            throw new EOFException("FLAC frame ends before its last byte!");
        }
        final var value = buffer[position++] & 0xFF;
        crc8 = CRC8_TABLE[crc8 ^ value];
        crc16 = ((crc16 << 8) ^ CRC16_TABLE[(crc16 >>> 8) ^ value]) & 0xFFFF;
        return value;
    }

    private boolean fill() throws IOException {
        final var read = inputStream.read(buffer);
        position = 0;
        limit = Math.max(read, 0);
        return read > 0;
    }

    // A table of the CRC of each byte, for a CRC of the given width with no reflection and no final XOR.
    private static int[] crcTable(final int polynomial, final int width) {
        final var topBit = 1 << (width - 1);
        final var mask = (1 << width) - 1;
        final var table = new int[256];
        for (var value = 0; value < 256; value++) {
            var crc = value << (width - 8);
            for (var bit = 0; bit < 8; bit++) {
                crc = (crc & topBit) != 0 ? (crc << 1) ^ polynomial : crc << 1;
            }
            table[value] = crc & mask;
        }
        return table;
    }
}
