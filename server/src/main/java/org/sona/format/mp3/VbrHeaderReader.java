package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import org.apache.commons.io.input.BoundedInputStream;
import org.sona.format.mp3.FirstFrame.AudioFrame;
import org.sona.format.mp3.FirstFrame.VbrHeader;

/**
 * Reads the VBR header an encoder may put in an MP3's first frame: a Xing header (written as Info at a constant
 * bitrate), which LAME and FFmpeg follow with a LAME tag, or Fraunhofer's VBRI header.
 */
final class VbrHeaderReader {

    // "Xing", "Info" and "VBRI".
    private static final long XING = 0x58_69_6E_67;
    private static final long INFO = 0x49_6E_66_6F;
    private static final long VBRI = 0x56_42_52_49;
    private static final long NO_ID = 0;
    private static final int ID_SIZE = 4;

    // Xing's flags say which of its fields are present, in this order.
    private static final int FRAMES_FLAG = 0x1;
    private static final int BYTES_FLAG = 0x2;
    private static final int TOC_FLAG = 0x4;
    private static final int QUALITY_FLAG = 0x8;
    private static final int TOC_SIZE = 100;

    // VBRI always starts 32 bytes after the frame header, and its frame count follows its version, delay, quality and
    // byte count.
    private static final int VBRI_OFFSET = FrameHeader.SIZE + 32;
    private static final int VBRI_FIELDS_SIZE = 10;

    // The encoders whose Xing headers a LAME tag follows, as FFmpeg checks for them: "LAME", "Lavf" and "Lavc".
    private static final long LAME = 0x4C_41_4D_45;
    private static final long LAVF = 0x4C_61_76_66;
    private static final long LAVC = 0x4C_61_76_63;
    // The tag packs the delay and padding into three bytes, twelve bits each, after the encoder and 17 more bytes.
    private static final int LAME_DELAY_OFFSET = 17;

    /**
     * Reads the first frame up to the end of its VBR header.
     *
     * @param frame the frame after its header, which ends where the frame does
     */
    FirstFrame readFirstFrame(final FrameHeader header, final BoundedInputStream frame) throws IOException {
        final var bits = BitInputStream.wrap(frame, ByteOrder.BIG_ENDIAN);
        // FFmpeg and mpg123 look for a Xing header straight after the side information, whether or not the frame has
        // a CRC.
        final var id = readId(frame, bits, header.sideInfoSize());
        if (id == XING || id == INFO) {
            return readXingHeader(frame, bits);
        }
        return readVbriHeader(header, frame, bits, id);
    }

    // Its frame count is optional. Without it, the frames are counted instead, taking this one for audio, which it is:
    // a frame of silence.
    private static FirstFrame readXingHeader(final BoundedInputStream frame, final BitInputStream bits)
            throws IOException {
        final var flags = bits.readBits(32);
        if ((flags & FRAMES_FLAG) == 0) {
            return new AudioFrame();
        }
        final var frameCount = bits.readBits(32);
        frame.skipNBytes(xingFieldsSize(flags));
        return readLameTag(frame, bits, frameCount);
    }

    // The fields after the frame count.
    private static int xingFieldsSize(final long flags) {
        var size = 0;
        if ((flags & BYTES_FLAG) != 0) {
            size += Integer.BYTES;
        }
        if ((flags & TOC_FLAG) != 0) {
            size += TOC_SIZE;
        }
        if ((flags & QUALITY_FLAG) != 0) {
            size += Integer.BYTES;
        }
        return size;
    }

    // Other encoders may leave the rest of the frame empty, or fill it with their own data.
    private static VbrHeader readLameTag(
            final BoundedInputStream frame, final BitInputStream bits, final long frameCount) throws IOException {
        final var encoder = readId(frame, bits, 0);
        if (encoder != LAME && encoder != LAVF && encoder != LAVC) {
            return new VbrHeader(frameCount, false, 0, 0);
        }
        frame.skipNBytes(LAME_DELAY_OFFSET);
        final var delay = (int) bits.readBits(12);
        final var padding = (int) bits.readBits(12);
        return new VbrHeader(frameCount, true, delay, padding);
    }

    // In MPEG-1 stereo, the Xing header's place is VBRI's too, so the ID read there is VBRI's.
    private static FirstFrame readVbriHeader(
            final FrameHeader header, final BoundedInputStream frame, final BitInputStream bits, final long xingId)
            throws IOException {
        final var gap = VBRI_OFFSET - (FrameHeader.SIZE + header.sideInfoSize() + ID_SIZE);
        final var id = vbriId(frame, bits, gap, xingId);
        if (id != VBRI) {
            return new AudioFrame();
        }
        frame.skipNBytes(VBRI_FIELDS_SIZE);
        return new VbrHeader(bits.readBits(32), false, 0, 0);
    }

    private static long vbriId(
            final BoundedInputStream frame, final BitInputStream bits, final int gap, final long xingId)
            throws IOException {
        if (gap < 0) {
            return xingId;
        }
        return readId(frame, bits, gap);
    }

    // The ID after skipping that many bytes, or none when the frame ends first, as a short frame of audio may.
    private static long readId(final BoundedInputStream frame, final BitInputStream bits, final int skip)
            throws IOException {
        if (frame.getRemaining() < skip + ID_SIZE) {
            return NO_ID;
        }
        frame.skipNBytes(skip);
        return bits.readBits(32);
    }
}
