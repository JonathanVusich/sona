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

    // The longest a free-format frame can be: 640 kbit/s, the most LAME and mpg123 allow, at MPEG-2.5's lowest sample
    // rate, padded.
    private static final int MAX_FREE_FORMAT_LENGTH = 72 * 640_000 / 8000 + 1;
    // The header bits every frame of a free-format stream shares: the sync, version, layer, bitrate index (0) and
    // sample rate index.
    private static final long FREE_FORMAT_STREAM_BITS = 0xFFFE_FC00L;

    private final VbrHeaderReader vbrHeaderReader = new VbrHeaderReader();

    /**
     * @param input the file's stream, at the first frame
     * @param audioStart where the first frame starts: the end of the ID3v2 tag, or 0 when the file has none
     * @param audioEnd where the last frame ends: the start of the tags after the audio, or the end of the file
     */
    Mp3StreamInfo readStreamInfo(final InputStream input, final long audioStart, final long audioEnd)
            throws IOException, InvalidFormatException {
        checkHeaderFits(audioStart, audioEnd);
        final var bits = BitInputStream.wrap(input, ByteOrder.BIG_ENDIAN);
        final var streamBits = bits.peekBits(32) & FREE_FORMAT_STREAM_BITS;
        final var header = new FrameHeader(bits);
        if (header.bitrate() == FrameHeader.FREE_FORMAT_BITRATE) {
            return readFreeFormatStream(input, bits, header, streamBits, audioStart, audioEnd);
        }
        checkFrameFits(audioStart, header, audioEnd);
        return readFirstFrame(input, bits, header, audioStart, audioEnd);
    }

    private Mp3StreamInfo readFirstFrame(
            final InputStream input,
            final BitInputStream bits,
            final FrameHeader header,
            final long audioStart,
            final long audioEnd)
            throws IOException, InvalidFormatException {
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

    // A free-format frame's header gives no length: the next frame's sync does, and every frame of the stream has that
    // length but for its padding. Encoders write no VBR header in free format, so the first frame is audio.
    private static Mp3StreamInfo readFreeFormatStream(
            final InputStream input,
            final BitInputStream bits,
            final FrameHeader header,
            final long streamBits,
            final long audioStart,
            final long audioEnd)
            throws IOException, InvalidFormatException {
        input.skipNBytes(header.sideInfoSize());
        final var length =
                findNextFrame(bits, streamBits, audioStart, audioEnd, FrameHeader.SIZE + header.sideInfoSize());
        final var firstHeader = header.withFreeFormatLength(length - header.padding());
        return streamInfo(
                firstHeader, audioStart, countFrames(input, bits, firstHeader, audioStart + length, audioEnd));
    }

    // How far after the first frame's start the next one starts, looking a byte at a time from the given distance. A
    // stream of one frame runs to the end of the audio.
    private static int findNextFrame(
            final BitInputStream bits,
            final long streamBits,
            final long audioStart,
            final long audioEnd,
            final int searchStart)
            throws IOException, InvalidFormatException {
        for (var length = searchStart; length <= MAX_FREE_FORMAT_LENGTH; length++) {
            if (audioStart + length + FrameHeader.SIZE > audioEnd) {
                return (int) (audioEnd - audioStart);
            }
            if ((bits.peekBits(32) & FREE_FORMAT_STREAM_BITS) == streamBits) {
                return length;
            }
            bits.readBits(8);
        }
        throw new InvalidFormatException(
                "Free-format MP3 frame at byte %d has no next frame to give its length!".formatted(audioStart));
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
    // keep the first one's version and sample rate, and a free-format frame has its length but for its padding.
    private static VbrHeader countFrames(
            final InputStream input,
            final BitInputStream bits,
            final FrameHeader firstHeader,
            final long secondFrame,
            final long audioEnd)
            throws IOException, InvalidFormatException {
        final var unpaddedLength = firstHeader.frameLength() - firstHeader.padding();
        var frameCount = 1L;
        for (var offset = secondFrame; offset < audioEnd; frameCount++) {
            checkHeaderFits(offset, audioEnd);
            final var header = withLength(new FrameHeader(bits), unpaddedLength);
            if (!firstHeader.isSameStream(header)) {
                throw new InvalidFormatException(
                        "MP3 frame at byte %d changes the stream's MPEG version or sample rate!".formatted(offset));
            }
            checkFrameFits(offset, header, audioEnd);
            input.skipNBytes(header.frameLength() - FrameHeader.SIZE);
            offset += header.frameLength();
        }
        return new VbrHeader(frameCount, 0, 0);
    }

    private static FrameHeader withLength(final FrameHeader header, final int unpaddedLength) {
        if (header.bitrate() == FrameHeader.FREE_FORMAT_BITRATE) {
            return header.withFreeFormatLength(unpaddedLength);
        }
        return header;
    }

    private static void checkHeaderFits(final long offset, final long audioEnd) throws InvalidFormatException {
        if (offset + FrameHeader.SIZE > audioEnd) {
            throw new InvalidFormatException("MP3 ends in a frame header at byte %d!".formatted(offset));
        }
    }

    private static void checkFrameFits(final long offset, final FrameHeader header, final long audioEnd)
            throws InvalidFormatException {
        if (offset + header.frameLength() > audioEnd) {
            throw new InvalidFormatException("MP3 frame at byte %d is cut short!".formatted(offset));
        }
    }
}
