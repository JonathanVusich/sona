package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import org.apache.commons.io.input.BoundedInputStream;
import org.sona.exception.InvalidFormatException;
import org.sona.format.mp3.FirstFrame.AudioFrame;
import org.sona.format.mp3.FirstFrame.VbrHeader;

/**
 * Reads an MP3's first frame, and works out how long its stream is from the frame's VBR header, or by counting its
 * frames when it has none. The audio must be nothing but Layer III frames of one stream. Every header is whole bytes,
 * so the bit stream holds no bits between frames, and the stream skips what Sona doesn't read.
 */
final class StreamInfoReader {

    private final VbrHeaderReader vbrHeaderReader = new VbrHeaderReader();

    /**
     * @param input the file's stream, at the first frame
     * @param audioStart where the first frame starts: the end of the ID3v2 tag, or 0 when the file has none
     * @param audioEnd where the last frame ends: the start of the ID3v1 tag, or the end of the file when it has none
     */
    Mp3StreamInfo readStreamInfo(final InputStream input, final long audioStart, final long audioEnd)
            throws IOException, InvalidFormatException {
        final var bits = BitInputStream.wrap(input, ByteOrder.BIG_ENDIAN);
        final var header = readFrameHeader(bits, audioStart, audioEnd);
        final var frame = BoundedInputStream.builder()
                .setInputStream(input)
                .setMaxCount(header.frameLength() - FrameHeader.SIZE)
                .setPropagateClose(false)
                .get();
        final var firstFrame = vbrHeaderReader.readFirstFrame(header, frame);
        frame.skipNBytes(frame.getRemaining());
        final var nextFrame = audioStart + header.frameLength();
        return switch (firstFrame) {
            case VbrHeader vbrHeader -> streamInfo(header, nextFrame, vbrHeader);
            case AudioFrame _ -> streamInfo(header, audioStart, countFrames(input, bits, header, nextFrame, audioEnd));
        };
    }

    private static Mp3StreamInfo streamInfo(
            final FrameHeader firstHeader, final long audioStart, final VbrHeader length) {
        return new Mp3StreamInfo(
                audioStart,
                firstHeader.sampleRate(),
                firstHeader.channelCount(),
                firstHeader.version().getSamplesPerFrame(),
                length.frameCount(),
                length.encoderDelay(),
                length.encoderPadding());
    }

    // The encoder's delay and padding are unknown without a VBR header, so they're counted as audio. Every frame must
    // keep the first one's version and sample rate.
    private static VbrHeader countFrames(
            final InputStream input,
            final BitInputStream bits,
            final FrameHeader firstHeader,
            final long secondFrame,
            final long audioEnd)
            throws IOException, InvalidFormatException {
        var frameCount = 1L;
        for (var offset = secondFrame; offset < audioEnd; frameCount++) {
            final var header = readFrameHeader(bits, offset, audioEnd);
            if (!firstHeader.isSameStream(header)) {
                throw new InvalidFormatException(
                        "MP3 frame at byte %d changes the stream's MPEG version or sample rate!".formatted(offset));
            }
            input.skipNBytes(header.frameLength() - FrameHeader.SIZE);
            offset += header.frameLength();
        }
        return new VbrHeader(frameCount, 0, 0);
    }

    // The header of a frame that starts at the offset, and must end by the end of the audio.
    private static FrameHeader readFrameHeader(final BitInputStream bits, final long offset, final long audioEnd)
            throws IOException, InvalidFormatException {
        if (offset + FrameHeader.SIZE > audioEnd) {
            throw new InvalidFormatException("MP3 ends in a frame header at byte %d!".formatted(offset));
        }
        final var header = new FrameHeader(bits);
        if (offset + header.frameLength() > audioEnd) {
            throw new InvalidFormatException("MP3 frame at byte %d is cut short!".formatted(offset));
        }
        return header;
    }
}
