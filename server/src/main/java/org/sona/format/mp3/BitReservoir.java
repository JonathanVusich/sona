package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.apache.commons.io.IOUtils;

/**
 * Layer III's bit reservoir (ISO/IEC 11172-3 section 2.4.3.4.1.1): a frame's main data may start in the main data of
 * the frames before it, up to 511 bytes back, so the decoder keeps the last frames' main data. This is the one place
 * the decoder reads bytes into an array: they're read again when a later frame reaches back into them.
 */
final class BitReservoir {

    // The furthest back main_data_begin can reach, in its nine bits.
    private static final int MAX_MAIN_DATA_BEGIN = 511;
    // The most main data a frame can hold: a free-format MPEG-2.5 frame at 640 kbit/s and 8 kHz.
    private static final int MAX_MAIN_DATA_SIZE = 72 * 640_000 / 8000 + 1;
    // Zeros after the main data, as FFmpeg's buffers have, which a granule's channel reads when its part2_3_length runs
    // past the main data: up to its 4095 bits, and the 19 a Huffman code is looked up by peeking.
    private static final int PADDING = (4095 + 19) / Byte.SIZE + 1;

    private final byte[] bytes = new byte[MAX_MAIN_DATA_BEGIN + MAX_MAIN_DATA_SIZE + PADDING];
    private int size;

    /**
     * Reads a frame's main data from the stream into the reservoir. The frame's granules start the side information's
     * main_data_begin bytes before the frame's own main data, which must be read before the next frame's main data is.
     *
     * @param input the frame's stream, at its main data
     * @return the frame, whose granules may start before the reservoir's first byte, when the stream was cut from a
     *     longer one
     */
    Mp3Frame readMainData(final InputStream input, final FrameHeader header, final SideInfo sideInfo)
            throws IOException {
        keepLastBytes();
        final var granulesStart = size - sideInfo.mainDataBegin();
        IOUtils.readFully(input, bytes, size, header.mainDataSize());
        size += header.mainDataSize();
        Arrays.fill(bytes, size, size + PADDING, (byte) 0);
        return new Mp3Frame(header, sideInfo, granulesStart);
    }

    /**
     * Starts the reservoir again with a frame's main data alone, for a frame FFmpeg drops, which keeps only the end of
     * that frame for the frames after it.
     *
     * @param input the frame's stream, at its main data
     */
    void restart(final InputStream input, final FrameHeader header) throws IOException {
        size = 0;
        IOUtils.readFully(input, bytes, 0, header.mainDataSize());
        size = header.mainDataSize();
    }

    /**
     * @param bitOffset how many bits after the frame's granules start to read from, which must be in the reservoir
     * @return the reservoir's bits from there, then zeros
     */
    BitInputStream bitsAt(final Mp3Frame frame, final int bitOffset) throws IOException {
        final var position = frame.granulesStart() * Byte.SIZE + bitOffset;
        final var start = position / Byte.SIZE;
        final var bits = BitInputStream.wrap(
                new ByteArrayInputStream(bytes, start, size + PADDING - start), ByteOrder.BIG_ENDIAN);
        final var skipped = position % Byte.SIZE;
        if (skipped > 0) {
            bits.readBits(skipped);
        }
        return bits;
    }

    // Only the last 511 bytes can be reached back to.
    private void keepLastBytes() {
        final var kept = Math.min(size, MAX_MAIN_DATA_BEGIN);
        System.arraycopy(bytes, size - kept, bytes, 0, kept);
        size = kept;
    }
}
