package org.sona.format.flac;

import dev.javax.bitstream.BitInputStream;
import dev.javax.bitstream.ByteSource;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

/**
 * Reads the bits of FLAC frames, most significant first, and keeps the CRC-8 of the frame header and the CRC-16 of
 * the whole frame (RFC 9639 sections 9.1.8 and 9.3) over the bytes read since the frame started.
 */
final class FrameReader {

    private static final int[] CRC8_TABLE = crcTable(0x07, 8);
    private static final int[] CRC16_TABLE = crcTable(0x8005, 16);

    private final InputStream inputStream;
    private final BitInputStream bits;
    private int crc8;
    private int crc16;
    private int bytesInFrame;

    FrameReader(final InputStream inputStream) {
        this.inputStream = inputStream;
        this.bits = BitInputStream.wrap(new FrameByteSource(), ByteOrder.BIG_ENDIAN);
    }

    /**
     * Starts the CRCs and the count of the frame's bytes over for a new frame.
     */
    void startFrame() {
        crc8 = 0;
        crc16 = 0;
        bytesInFrame = 0;
    }

    /**
     * @param count from 1 to 63
     */
    long readBits(final int count) throws IOException {
        return bits.readBits(count);
    }

    /**
     * @param count from 1 to 63
     */
    long readSigned(final int count) throws IOException {
        final var shift = 64 - count;
        return (bits.readBits(count) << shift) >> shift;
    }

    /**
     * @return the number of 0 bits before the next 1 bit, which is read too
     */
    int readUnary() throws IOException {
        var zeros = 0;
        while (bits.readBits(1) == 0) {
            zeros++;
        }
        return zeros;
    }

    /**
     * Skips to the start of the next byte, as each frame's subframes are padded to whole bytes.
     */
    void alignToByte() {
        bits.alignToByte();
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

    /**
     * @return how many of the frame's bytes have been read, even when a read ran out of bytes part-way
     */
    int bytesInFrame() {
        return bytesInFrame;
    }

    // The bit stream only takes the bytes whose bits it needs, so the CRCs cover exactly the frame's bytes read.
    // It buffers the stream itself, as the bit stream takes a byte or two at a time, and a BufferedInputStream's checks
    // and locking on each of those reads cost more than decoding them.
    private final class FrameByteSource implements ByteSource {

        private final byte[] buffer = new byte[64 * 1024];
        private int position;
        private int limit;

        @Override
        public void read(final byte[] destination, final int numBytes) throws IOException {
            for (var i = 0; i < numBytes; i++) {
                if (position == limit && !fill()) {
                    throw new EOFException("FLAC stream ends in the middle of a frame!");
                }
                final var value = buffer[position++] & 0xFF;
                destination[i] = (byte) value;
                crc8 = CRC8_TABLE[crc8 ^ value];
                crc16 = ((crc16 << 8) ^ CRC16_TABLE[(crc16 >>> 8) ^ value]) & 0xFFFF;
                bytesInFrame++;
            }
        }

        // @return false at the end of the stream
        private boolean fill() throws IOException {
            final var read = inputStream.read(buffer);
            position = 0;
            limit = Math.max(read, 0);
            return read > 0;
        }

        @Override
        public void close() {
            // The decoder's caller owns the stream.
        }
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
