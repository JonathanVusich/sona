package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.apache.commons.io.IOUtils;
import org.sona.exception.InvalidFormatException;

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
    // Zero bytes after the main data, as Huffman codes are looked up by peeking up to 19 bits ahead of the last one.
    private static final int PADDING = 3;

    private final byte[] bytes = new byte[MAX_MAIN_DATA_BEGIN + MAX_MAIN_DATA_SIZE + PADDING];
    private int size;

    /**
     * Reads a frame's main data from the stream into the reservoir. The frame's granules start the side information's
     * main_data_begin bytes before the frame's own main data, and must be read before the next frame's main data is.
     *
     * @param input the frame's stream, at its main data
     * @return the frame, with its main data from where its granules start
     * @throws InvalidFormatException when the frame's granules start before the stream's main data does, as in a
     *     stream cut from a longer one, though its own main data is still kept for the frames after it
     */
    Mp3Frame readMainData(final InputStream input, final FrameHeader header, final SideInfo sideInfo)
            throws IOException, InvalidFormatException {
        keepLastBytes();
        final var start = size - sideInfo.mainDataBegin();
        IOUtils.readFully(input, bytes, size, header.mainDataSize());
        size += header.mainDataSize();
        if (start < 0) {
            throw new InvalidFormatException("MP3 frame's main data starts %d bytes back, before the stream's does!"
                    .formatted(sideInfo.mainDataBegin()));
        }
        Arrays.fill(bytes, size, size + PADDING, (byte) 0);
        final var mainData = new ByteArrayInputStream(bytes, start, size - start + PADDING);
        return new Mp3Frame(header, sideInfo, BitInputStream.wrap(mainData, ByteOrder.BIG_ENDIAN), size - start);
    }

    // Only the last 511 bytes can be reached back to.
    private void keepLastBytes() {
        final var kept = Math.min(size, MAX_MAIN_DATA_BEGIN);
        System.arraycopy(bytes, size - kept, bytes, 0, kept);
        size = kept;
    }
}
